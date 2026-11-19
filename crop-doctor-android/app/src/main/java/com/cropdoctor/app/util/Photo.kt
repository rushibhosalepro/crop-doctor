package com.cropdoctor.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object Photo {
    const val MAX_DIM = 1024

    /** Laplacian-variance threshold. Tunable; calibrate on the eval set. */
    const val SHARPNESS_THRESHOLD = 80.0

    fun newFile(context: android.content.Context): File {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        return File(dir, "leaf_${System.currentTimeMillis()}.jpg")
    }

    fun decodeSampled(path: String, maxDim: Int = MAX_DIM): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Variance of Laplacian on a small grayscale copy. Higher = sharper. */
    fun sharpnessScore(src: Bitmap): Double {
        val w = 128
        val h = (src.height * w / src.width).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val gray = DoubleArray(w * h) { i ->
            val c = px[i]
            0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
        }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val lap = gray[(y - 1) * w + x] + gray[(y + 1) * w + x] +
                    gray[y * w + x - 1] + gray[y * w + x + 1] - 4.0 * gray[y * w + x]
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        small.recycle()
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Bundled demo samples, loaded by resource name. Returns null if missing. */
    fun sampleBitmap(context: android.content.Context, name: String, maxDim: Int = MAX_DIM): Bitmap? {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        return BitmapFactory.decodeResource(
            context.resources, id,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
