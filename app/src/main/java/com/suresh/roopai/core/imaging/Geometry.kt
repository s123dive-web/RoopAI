package com.suresh.roopai.core.imaging

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Crop / rotate / flip on pixel buffers. */
object Geometry {

    /** Rotates clockwise by [quarterTurns] * 90 degrees. */
    fun rotate90(src: PixelBuffer, quarterTurns: Int): PixelBuffer {
        val q = ((quarterTurns % 4) + 4) % 4
        if (q == 0) return src
        val w = src.width; val h = src.height
        val dw = if (q % 2 == 1) h else w
        val dh = if (q % 2 == 1) w else h
        val out = IntArray(dw * dh)
        for (y in 0 until h) for (x in 0 until w) {
            val (nx, ny) = when (q) {
                1 -> (h - 1 - y) to x
                2 -> (w - 1 - x) to (h - 1 - y)
                else -> y to (w - 1 - x)
            }
            out[ny * dw + nx] = src.px[y * w + x]
        }
        return PixelBuffer(dw, dh, out)
    }

    fun flip(src: PixelBuffer, horizontal: Boolean, vertical: Boolean): PixelBuffer {
        if (!horizontal && !vertical) return src
        val w = src.width; val h = src.height
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = if (horizontal) w - 1 - x else x
            val sy = if (vertical) h - 1 - y else y
            out[y * w + x] = src.px[sy * w + sx]
        }
        return PixelBuffer(w, h, out)
    }

    /**
     * Size of the largest axis-aligned rectangle with the source aspect ratio that fits inside
     * a w*h rectangle rotated by [degrees].
     */
    fun straightenScale(w: Int, h: Int, degrees: Float): Float {
        val a = abs(Math.toRadians(degrees.toDouble()))
        if (a < 1e-6) return 1f
        val c = cos(a); val s = sin(a)
        val k1 = w / (w * c + h * s)
        val k2 = h / (w * s + h * c)
        return min(k1, k2).toFloat()
    }

    /** Rotates by a small angle around the centre and crops so no empty corners remain. */
    fun straighten(src: PixelBuffer, degrees: Float): PixelBuffer {
        if (abs(degrees) < 0.05f) return src
        val w = src.width; val h = src.height
        val k = straightenScale(w, h, degrees)
        val dw = max(1, (w * k).toInt()); val dh = max(1, (h * k).toInt())
        val out = IntArray(dw * dh)
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
        val cx = w / 2f; val cy = h / 2f
        for (y in 0 until dh) for (x in 0 until dw) {
            val dx = x + 0.5f - dw / 2f; val dy = y + 0.5f - dh / 2f
            // inverse rotation: output -> source
            val sx = (cx + dx * c + dy * s - 0.5f).coerceIn(0f, w - 1f)
            val sy = (cy - dx * s + dy * c - 0.5f).coerceIn(0f, h - 1f)
            out[y * dw + x] = WarpField.bilinear(src.px, w, h, sx, sy)
        }
        return PixelBuffer(dw, dh, out)
    }

    /** Converts a normalised crop (0..1) to a valid pixel rect of at least 8x8 (or the image). */
    fun cropRect(w: Int, h: Int, l: Float, t: Float, r: Float, b: Float): IRect {
        val x0 = (min(l, r).coerceIn(0f, 1f) * w).roundToInt()
        val y0 = (min(t, b).coerceIn(0f, 1f) * h).roundToInt()
        var x1 = (max(l, r).coerceIn(0f, 1f) * w).roundToInt()
        var y1 = (max(t, b).coerceIn(0f, 1f) * h).roundToInt()
        val minW = min(8, w); val minH = min(8, h)
        if (x1 - x0 < minW) x1 = min(w, x0 + minW)
        if (y1 - y0 < minH) y1 = min(h, y0 + minH)
        return IRect(max(0, x1 - max(x1 - x0, minW)), max(0, y1 - max(y1 - y0, minH)), x1, y1)
    }

    fun crop(src: PixelBuffer, rect: IRect): PixelBuffer {
        val r = rect.clip(src.width, src.height)
        if (r.isEmpty || (r.width == src.width && r.height == src.height)) return src
        val out = IntArray(r.width * r.height)
        for (y in 0 until r.height) {
            System.arraycopy(src.px, (r.top + y) * src.width + r.left, out, y * r.width, r.width)
        }
        return PixelBuffer(r.width, r.height, out)
    }

    /** Largest centred crop (normalised l,t,r,b) with the given aspect ratio (width / height). */
    fun centeredAspectCrop(w: Int, h: Int, aspect: Float): FloatArray {
        val cur = w.toFloat() / h
        return if (cur > aspect) {
            val nw = aspect / cur
            floatArrayOf((1 - nw) / 2, 0f, (1 + nw) / 2, 1f)
        } else {
            val nh = cur / aspect
            floatArrayOf(0f, (1 - nh) / 2, 1f, (1 + nh) / 2)
        }
    }

    /** Computes target size that fits [maxLongSide] and [maxPixels] keeping aspect ratio. */
    fun fitSize(w: Int, h: Int, maxLongSide: Int, maxPixels: Long = Long.MAX_VALUE): Pair<Int, Int> {
        var scale = 1.0
        val long = max(w, h)
        if (long > maxLongSide) scale = maxLongSide.toDouble() / long
        val px = w.toDouble() * h * scale * scale
        if (px > maxPixels) scale *= Math.sqrt(maxPixels / px)
        return max(1, (w * scale).toInt()) to max(1, (h * scale).toInt())
    }

    /** Power-of-two decode sample size so the decoded image is still >= the requested size. */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
        return s
    }
}
