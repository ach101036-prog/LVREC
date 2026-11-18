package com.ach101036.tikliver

import androidx.camera.core.ImageProxy

object YuvConverter {
    /** Packs ImageProxy (YUV_420_888) into a flat yuv420p byte array. */
    fun imageProxyToYuv420p(image: ImageProxy): ByteArray {
        val w = image.width
        val h = image.height
        val out = ByteArray(w * h + 2 * (w / 2) * (h / 2))

        val yP = image.planes[0]; val uP = image.planes[1]; val vP = image.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer

        val yRowStride = yP.rowStride
        val uRowStride = uP.rowStride
        val vRowStride = vP.rowStride
        val uPixStride = uP.pixelStride
        val vPixStride = vP.pixelStride

        var o = 0
        val row = ByteArray(w)
        for (r in 0 until h) {
            yB.position(r * yRowStride); yB.get(row, 0, w)
            System.arraycopy(row, 0, out, o, w); o += w
        }

        val uRow = ByteArray(w / 2)
        for (r in 0 until h / 2) {
            val start = r * uRowStride
            for (c in 0 until w / 2) uRow[c] = uB.get(start + c * uPixStride)
            System.arraycopy(uRow, 0, out, o, w / 2); o += w / 2
        }

        val vRow = ByteArray(w / 2)
        for (r in 0 until h / 2) {
            val start = r * vRowStride
            for (c in 0 until w / 2) vRow[c] = vB.get(start + c * vPixStride)
            System.arraycopy(vRow, 0, out, o, w / 2); o += w / 2
        }
        return out
    }
}
