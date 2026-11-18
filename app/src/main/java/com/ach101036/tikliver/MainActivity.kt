package com.ach101036.tikliver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Size
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var statusText: TextView
    private lateinit var outputPathText: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recordingRequested = AtomicBoolean(false)
    @Volatile private var recorder: FifoRecorder? = null
    @Volatile private var pendingOutput: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        statusText = findViewById(R.id.statusText)
        outputPathText = findViewById(R.id.outputPathText)

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQ)

        recordButton.setOnClickListener {
            if (recordingRequested.get()) stopRecording() else startRecording()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == REQ) {
            if (allPermissionsGranted()) startCamera()
            else { Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show(); finish() }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(Size(640, 480))
                .build()

            analysis.setAnalyzer(cameraExecutor) { image ->
                try {
                    if (recordingRequested.get()) {
                        // Lazy-init recorder with actual frame dims on first frame.
                        if (recorder == null) {
                            val out = pendingOutput ?: return@setAnalyzer
                            val r = FifoRecorder(
                                this,
                                image.width, image.height, fps = 30,
                                rotationDegrees = image.imageInfo.rotationDegrees,
                            )
                            if (r.start(out)) recorder = r
                        }
                        recorder?.writeFrame(YuvConverter.imageProxyToYuv420p(image))
                    }
                } finally { image.close() }
            }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            statusText.text = "Camera ready"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        if (recordingRequested.get()) return
        val out = File(
            getExternalFilesDir(null),
            "TikLive_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        )
        pendingOutput = out
        outputPathText.text = out.absolutePath
        recordingRequested.set(true)
        recordButton.text = "Stop Recording"
        statusText.text = "Starting…"
    }

    private fun stopRecording() {
        if (!recordingRequested.get()) return
        recordingRequested.set(false)
        recordButton.text = "Start Recording"
        statusText.text = "Finalizing…"
        val r = recorder
        recorder = null
        Thread {
            r?.stop()
            runOnUiThread { statusText.text = "Saved" }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingRequested.set(false)
        recorder?.stop()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQ = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
