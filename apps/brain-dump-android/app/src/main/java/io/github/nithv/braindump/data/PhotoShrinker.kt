package io.github.nithv.braindump.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Same size rule as the website (apps/brain-dump/src/lib/photo.ts): longest side at most 1600 px. */
const val MAX_PHOTO_SIDE = 1600

/** A photo that's still too big after shrinking is refused (same 5 MB limit as the website). */
const val MAX_PHOTO_BYTES = 5L * 1024 * 1024

class PhotoTooBigException : IOException("Photo too big even after shrinking")

/**
 * Turns a camera or gallery photo into a small WebP: right way up, longest side ≤ 1600 px.
 *
 * Re-encoding writes brand-new pixels with NO metadata, so hidden EXIF data (GPS location, phone
 * model, time) is dropped by construction rather than by remembering to strip it.
 */
object PhotoShrinker {

    /** Halves the decode size while it stays at least [target] px, to save memory on 50 MP photos. */
    fun sampleSize(width: Int, height: Int, target: Int = MAX_PHOTO_SIDE): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Final size: longest side [target] px or less, never enlarged, aspect ratio kept. */
    fun scaledSize(width: Int, height: Int, target: Int = MAX_PHOTO_SIDE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= target) return width to height
        val scale = target.toDouble() / longest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    /** How much to turn the picture so it's upright, from its EXIF orientation tag. */
    fun rotationDegrees(exifOrientation: Int): Float = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }

    /**
     * Shrinks the photo that [open] reads (called more than once: for size, orientation, pixels)
     * into [out]. Throws if it isn't a readable image.
     */
    fun shrink(open: () -> InputStream, out: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

        val orientation = runCatching {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("Couldn't read photo")

        val (w, h) = scaledSize(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            postRotate(rotationDegrees(orientation))
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        try {
            out.outputStream().use { stream ->
                @Suppress("DEPRECATION") // WEBP is lossy below quality 100 on older Androids
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                if (!upright.compress(format, 80, stream)) throw IOException("Couldn't save photo")
                stream.fd.sync() // on disk for real before we call it done
            }
        } finally {
            upright.recycle()
        }
        if (out.length() > MAX_PHOTO_BYTES) {
            out.delete()
            throw PhotoTooBigException()
        }
    }
}
