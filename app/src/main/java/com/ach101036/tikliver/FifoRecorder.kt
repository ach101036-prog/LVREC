package com.ach101036.tikliver

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records raw yuv420p frames to a named pipe (FIFO). FFmpeg reads the FIFO,
 * encodes H.264, and writes an MP4 file. Frames are supplied by [writeFrame].
 */
class FifoRecorder(
    private val context: Context,
    val width: Int,
    val height: Int,
    private val fps: Int,
    private val rotationDegrees: Int,
) {
    private val fifo = File(context.cacheDir, "tiklive.fifo")
    private var writer: OutputStream? = null
    private val recording = AtomicBoolean(false)
    private var session: FFmpegSession? = null
    private val latch = CountDownLatch(1)

    fun start(outputFile: File): Boolean {
        if (recording.get()) return false
        if (fifo.exists()) fifo.delete()

        val mk = Runtime.getRuntime().exec(arrayOf("mkfifo", fifo.absolutePath))
        if (mk.waitFor() != 0) return false

        val vf = when (rotationDegrees) {
            90 -> "-vf transpose=1"
            180 -> "-vf transpose=1,transpose=1"
            270 -> "-vf transpose=2"
            else -> ""
        }
        val cmd = buildString {
            append("-f rawvideo -pix_fmt yuv420p -s ${width}x$height -r $fps ")
            append("-i ${fifo.absolutePath} ")
            if (vf.isNotEmpty()) append("$vf ")
            append("-c:v libx264 -preset ultrafast -pix_fmt yuv420p ")
            append("-movflags +faststart -y ${outputFile.absolutePath}")
        }

        // Start FFmpeg first — its open() blocks until a writer connects.
        session = FFmpegKit.executeAsync(cmd) { latch.countDown() }

        // Open writer; this unblocks FFmpeg.
        writer = FileOutputStream(fifo.absolutePath)
        recording.set(true)
        return true
    }

    fun writeFrame(yuv: ByteArray) {
        if (!recording.get()) return
        try { writer?.write(yuv) } catch (_: Exception) {}
    }

    fun stop() {
        if (!recording.get()) return
        recording.set(false)
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        // Wait for FFmpeg to finalize the MP4.
        latch.await(15, TimeUnit.SECONDS)
        session = null
        if (fifo.exists()) fifo.delete()
    }
}
