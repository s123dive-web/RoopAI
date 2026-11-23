package com.suresh.roopai.core.imaging

import kotlin.math.max
import kotlin.math.min

/** Colour helpers on packed ARGB ints. */
object Colors {
    fun a(c: Int) = c ushr 24
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF

    fun clamp8(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
    fun clamp8(v: Float) = clamp8((v + 0.5f).toInt())

    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int) = argb(255, clamp8(r), clamp8(g), clamp8(b))

    /** Rec.601 luma, 0..255. */
    fun luma(c: Int): Float = 0.299f * r(c) + 0.587f * g(c) + 0.114f * b(c)

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun smoothstep(lo: Float, hi: Float, x: Float): Float {
        if (hi <= lo) return if (x >= hi) 1f else 0f
        val t = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Mix [dst] towards [src] by [t] (0..1), keeping dst alpha. */
    fun mix(dst: Int, src: Int, t: Float): Int {
        if (t <= 0f) return dst
        val k = if (t > 1f) 1f else t
        val r = r(dst) + (r(src) - r(dst)) * k
        val g = g(dst) + (g(src) - g(dst)) * k
        val b = b(dst) + (b(src) - b(dst)) * k
        return argb(a(dst), clamp8(r), clamp8(g), clamp8(b))
    }

    fun multiply(dst: Int, src: Int): Int = argb(
        a(dst), r(dst) * r(src) / 255, g(dst) * g(src) / 255, b(dst) * b(src) / 255,
    )

    /**
     * Re-colours [dst] with [color] while keeping dst's light/shadow structure.
     * A pixel whose luma equals [pivot] becomes exactly [color]; darker pixels become a darker
     * shade, brighter ones blow out towards white (keeps specular highlights).
     */
    fun tintKeepLuma(dst: Int, color: Int, pivot: Float): Int {
        val l = luma(dst)
        val k = l / max(pivot, 1f)
        var r = r(color) * k
        var g = g(color) * k
        var b = b(color) * k
        if (k > 1f) {
            // push the overshoot towards white instead of clipping channel by channel
            val over = min((k - 1f) * 0.6f, 1f)
            r = min(r, 255f); g = min(g, 255f); b = min(b, 255f)
            r += (255f - r) * over; g += (255f - g) * over; b += (255f - b) * over
        }
        return argb(a(dst), clamp8(r), clamp8(g), clamp8(b))
    }

    /** Saturation 0..1 (HSV definition). */
    fun saturation(c: Int): Float {
        val mx = max(r(c), max(g(c), b(c)))
        val mn = min(r(c), min(g(c), b(c)))
        return if (mx == 0) 0f else (mx - mn) / mx.toFloat()
    }

    /** Scale saturation around luma. [s] = 1 keeps the pixel, 0 is greyscale. */
    fun saturate(c: Int, s: Float): Int {
        val l = luma(c)
        return argb(
            a(c),
            clamp8(l + (r(c) - l) * s), clamp8(l + (g(c) - l) * s), clamp8(l + (b(c) - l) * s),
        )
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return rgb((r * 255 + 0.5f).toInt(), (g * 255 + 0.5f).toInt(), (b * 255 + 0.5f).toInt())
    }

    /** Returns floatArrayOf(h 0..360, s 0..1, v 0..1). */
    fun rgbToHsv(c: Int): FloatArray {
        val r = r(c) / 255f
        val g = g(c) / 255f
        val b = b(c) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx)
    }
}
