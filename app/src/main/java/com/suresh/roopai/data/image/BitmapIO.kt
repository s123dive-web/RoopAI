package com.suresh.roopai.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.suresh.roopai.core.imaging.Geometry
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.edit.EditException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Bitmap decode / encode helpers: downsampled decoding, EXIF orientation, atomic writes. */
object BitmapIO {

    data class ImageInfo(val width: Int, val height: Int, val exifOrientation: Int) {
        private val swaps get() = exifOrientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )

        /** Size as the user sees it (after applying EXIF orientation). */
        val uprightWidth get() = if (swaps) height else width
        val uprightHeight get() = if (swaps) width else height
    }

    /** Reads size and orientation without decoding pixels. Throws [EditException.Unreadable]. */
    fun info(file: File): ImageInfo {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) throw EditException.Unreadable("unsupported or damaged file")
        val orientation = try {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return ImageInfo(o.outWidth, o.outHeight, orientation)
    }

    /**
     * Decodes [file] upright (EXIF applied) as a mutable-safe ARGB_8888 bitmap no larger than
     * [maxLongSide] / [maxPixels]. Large photos are subsampled by the decoder so a 50 MP image
     * never exists in memory at full size unless the budget allows it.
     */
    fun decode(file: File, maxLongSide: Int, maxPixels: Long = Long.MAX_VALUE): Bitmap {
        val info = info(file)
        val (tw, th) = Geometry.fitSize(info.uprightWidth, info.uprightHeight, maxLongSide, maxPixels)
        // target in the file's own (pre-rotation) orientation
        val swapped = info.uprightWidth != info.width
        val rw = if (swapped) th else tw
        val rh = if (swapped) tw else th
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Geometry.sampleSize(info.width, info.height, rw, rh)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val raw = try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (_: OutOfMemoryError) {
            throw EditException.OutOfMemory()
        } ?: throw EditException.Unreadable("decoder returned nothing")
        return try {
            val m = Matrix()
            if (raw.width != rw || raw.height != rh) m.postScale(rw / raw.width.toFloat(), rh / raw.height.toFloat())
            applyOrientation(m, info.exifOrientation)
            val out = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (out !== raw) raw.recycle()
            ensureArgb(out)
        } catch (_: OutOfMemoryError) {
            raw.recycle()
            throw EditException.OutOfMemory()
        }
    }

    private fun applyOrientation(m: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
    }

    private fun ensureArgb(b: Bitmap): Bitmap {
        if (b.config == Bitmap.Config.ARGB_8888) return b
        val c = b.copy(Bitmap.Config.ARGB_8888, false)
        b.recycle()
        return c
    }

    fun decodeBytes(bytes: ByteArray): Bitmap {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val b = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: OutOfMemoryError) {
            throw EditException.OutOfMemory()
        } ?: throw EditException.BadResponse("image data could not be decoded")
        return ensureArgb(b)
    }

    fun toPixels(b: Bitmap): PixelBuffer {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return PixelBuffer(b.width, b.height, px)
    }

    fun toBitmap(p: PixelBuffer): Bitmap = Bitmap.createBitmap(p.px, p.width, p.height, Bitmap.Config.ARGB_8888)

    /** Scales so the long side is at most [maxLongSide]; returns the same instance if it already fits. */
    fun scaleToFit(b: Bitmap, maxLongSide: Int): Bitmap {
        val (w, h) = Geometry.fitSize(b.width, b.height, maxLongSide)
        return if (w == b.width && h == b.height) b else b.scale(w, h)
    }

    /** Centre-crop [b] to exactly w*h pixels as ARGB ints. */
    fun centerCropPixels(b: Bitmap, w: Int, h: Int): IntArray {
        val scale = maxOf(w / b.width.toFloat(), h / b.height.toFloat())
        val sw = (w / scale).toInt().coerceIn(1, b.width)
        val sh = (h / scale).toInt().coerceIn(1, b.height)
        val region = Bitmap.createBitmap(b, (b.width - sw) / 2, (b.height - sh) / 2, sw, sh)
        val scaled = region.scale(w, h)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== region) scaled.recycle()
        if (region !== b) region.recycle()
        return px
    }

    fun jpegBytes(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun compress(b: Bitmap, format: OutputFormat, out: OutputStream) {
        val ok = when (format) {
            is OutputFormat.Jpeg -> b.compress(Bitmap.CompressFormat.JPEG, format.quality, out)
            OutputFormat.Png -> b.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!ok) throw java.io.IOException("image encoder failed")
    }
}

sealed interface OutputFormat {
    val mime: String
    val extension: String

    data class Jpeg(val quality: Int) : OutputFormat {
        override val mime = "image/jpeg"
        override val extension = "jpg"
    }

    data object Png : OutputFormat {
        override val mime = "image/png"
        override val extension = "png"
    }
}

/** Write-to-temp-then-rename so a crash or full disk never leaves a half-written file. */
object AtomicFiles {
    fun write(target: File, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                block(out)
                out.flush()
                try { out.fd.sync() } catch (_: Exception) { }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw java.io.IOException("could not move ${tmp.name} into place")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun writeBytes(target: File, bytes: ByteArray) = write(target) { it.write(bytes) }
}
