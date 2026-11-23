package com.suresh.roopai.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel-level retouch operations. Every function works inside a [RoiMask] so cost scales with
 * the face size, not the photo size. Radii are passed in pixels by the caller (derived from the
 * face size) which makes results resolution independent between preview and export.
 */
object Retouch {

    /** Copies the mask rectangle out of [buf]. */
    fun extract(buf: PixelBuffer, m: RoiMask): IntArray {
        val out = IntArray(m.width * m.height)
        for (y in 0 until m.height) {
            System.arraycopy(buf.px, (m.top + y) * buf.width + m.left, out, y * m.width, m.width)
        }
        return out
    }

    /** Mask-weighted mean colour, or null when the mask is empty. */
    fun meanColor(buf: PixelBuffer, m: RoiMask): Int? {
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var sw = 0.0
        for (y in 0 until m.height) for (x in 0 until m.width) {
            val w = m.a[y * m.width + x]
            if (w <= 0.01f) continue
            val c = buf.px[(m.top + y) * buf.width + m.left + x]
            sr += Colors.r(c) * w; sg += Colors.g(c) * w; sb += Colors.b(c) * w; sw += w
        }
        if (sw < 1e-3) return null
        return Colors.rgb((sr / sw).toInt(), (sg / sw).toInt(), (sb / sw).toInt())
    }

    /**
     * Multiplies the mask by how close each pixel's chroma is to [ref]. Removes hair strands,
     * glasses frames and background from a polygonal skin mask.
     */
    fun gateByChroma(buf: PixelBuffer, m: RoiMask, ref: Int, lo: Float = 30f, hi: Float = 75f) {
        val rl = Colors.luma(ref)
        val rcb = Colors.b(ref) - rl; val rcr = Colors.r(ref) - rl
        for (y in 0 until m.height) for (x in 0 until m.width) {
            val i = y * m.width + x
            if (m.a[i] <= 0f) continue
            val c = buf.px[(m.top + y) * buf.width + m.left + x]
            val l = Colors.luma(c)
            val d = abs(Colors.b(c) - l - rcb) + abs(Colors.r(c) - l - rcr) + abs(l - rl) * 0.25f
            m.a[i] *= 1f - Colors.smoothstep(lo, hi, d)
        }
    }

    /** Edge-preserving smoothing: low-pass differences are applied only where they are small. */
    fun smooth(buf: PixelBuffer, m: RoiMask, amount: Float, radius: Int) {
        if (amount <= 0f) return
        val src = extract(buf, m)
        val blur = Blur.boxRgb(src, m.width, m.height, max(1, radius))
        val k = min(amount, 1f) * 0.8f
        for (i in src.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = src[i]; val bl = blur[i]
            val e = abs(Colors.luma(bl) - Colors.luma(o))
            val keep = Colors.smoothstep(9f, 34f, e)
            val t = k * w * (1f - keep)
            if (t <= 0f) continue
            put(buf, m, i, Colors.mix(o, bl, t))
        }
    }

    /** Fades small dark or red spots that differ from their neighbourhood. */
    fun blemish(buf: PixelBuffer, m: RoiMask, amount: Float, smallR: Int, medR: Int) {
        if (amount <= 0f) return
        val src = extract(buf, m)
        val bs = Blur.boxRgb(src, m.width, m.height, max(1, smallR), 2)
        val bm = Blur.boxRgb(src, m.width, m.height, max(2, medR))
        for (i in src.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val s = bs[i]; val md = bm[i]
            val dark = Colors.luma(md) - Colors.luma(s)
            val red = (Colors.r(s) - Colors.g(s)) - (Colors.r(md) - Colors.g(md))
            val spot = Colors.smoothstep(3f, 12f, max(dark, red * 0.9f))
            val t = spot * min(amount, 1f) * w
            if (t <= 0f) continue
            val o = src[i]
            // neighbourhood colour + half of the fine texture so the patch is not plastic
            val r = Colors.r(md) + (Colors.r(o) - Colors.r(s)) * 0.5f
            val g = Colors.g(md) + (Colors.g(o) - Colors.g(s)) * 0.5f
            val b = Colors.b(md) + (Colors.b(o) - Colors.b(s)) * 0.5f
            put(buf, m, i, Colors.mix(o, Colors.rgb(r.toInt(), g.toInt(), b.toInt()), t))
        }
    }

    /**
     * Shifts the low-frequency colour towards [target] while keeping detail. Used for tone
     * evening (target = mean skin) and dark circles (target = cheek colour).
     */
    fun pullLowFrequency(buf: PixelBuffer, m: RoiMask, amount: Float, radius: Int, target: Int, strength: Float = 0.65f) {
        if (amount <= 0f) return
        val src = extract(buf, m)
        val blur = Blur.boxRgb(src, m.width, m.height, max(1, radius))
        val k = min(amount, 1f) * strength
        val tr = Colors.r(target); val tg = Colors.g(target); val tb = Colors.b(target)
        for (i in src.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = src[i]; val bl = blur[i]
            val t = k * w
            put(
                buf, m, i,
                Colors.argb(
                    Colors.a(o),
                    Colors.clamp8(Colors.r(o) + (tr - Colors.r(bl)) * t),
                    Colors.clamp8(Colors.g(o) + (tg - Colors.g(bl)) * t),
                    Colors.clamp8(Colors.b(o) + (tb - Colors.b(bl)) * t),
                ),
            )
        }
    }

    /** Fills fine dark creases (negative high-pass) and softens their bright rims. */
    fun softenLines(buf: PixelBuffer, m: RoiMask, amount: Float, radius: Int) {
        if (amount <= 0f) return
        val src = extract(buf, m)
        val blur = Blur.boxRgb(src, m.width, m.height, max(1, radius))
        val k = min(amount, 1f)
        for (i in src.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = src[i]; val bl = blur[i]
            val hp = Colors.luma(o) - Colors.luma(bl)
            val t = (if (hp < 0f) 0.92f else 0.45f) * k * w
            put(buf, m, i, Colors.mix(o, bl, t))
        }
    }

    /** Lifts brightness and clarity inside the eye opening; whites lose colour cast. */
    fun brightenEyes(buf: PixelBuffer, m: RoiMask, amount: Float) {
        if (amount <= 0f) return
        val k = min(amount, 1f)
        for (i in m.a.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = get(buf, m, i)
            val l = Colors.luma(o)
            val sat = Colors.saturation(o)
            val white = Colors.smoothstep(95f, 165f, l) * (1f - Colors.smoothstep(0.18f, 0.42f, sat))
            var c = Colors.saturate(o, 1f - 0.55f * white * k)
            val lift = (0.20f + 0.12f * white) * k * w
            // contrast around mid grey gives the iris some sparkle
            val gain = 1f + 0.18f * k * w
            c = Colors.argb(
                Colors.a(o),
                Colors.clamp8((Colors.r(c) - 110f) * gain + 110f + (255 - Colors.r(c)) * lift),
                Colors.clamp8((Colors.g(c) - 110f) * gain + 110f + (255 - Colors.g(c)) * lift),
                Colors.clamp8((Colors.b(c) - 110f) * gain + 110f + (255 - Colors.b(c)) * lift),
            )
            put(buf, m, i, Colors.mix(o, c, w))
        }
    }

    /** Whitening weight for a pixel inside the mouth: bright, not saturated, not red. */
    fun toothWeight(c: Int): Float {
        val l = Colors.luma(c)
        val bright = Colors.smoothstep(70f, 130f, l)
        val notRed = 1f - Colors.smoothstep(28f, 70f, (Colors.r(c) - Colors.g(c)).toFloat())
        val lowSat = 1f - Colors.smoothstep(0.38f, 0.62f, Colors.saturation(c))
        return bright * notRed * lowSat
    }

    fun whitenTeeth(buf: PixelBuffer, m: RoiMask, amount: Float) {
        if (amount <= 0f) return
        val k = min(amount, 1f)
        for (i in m.a.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = get(buf, m, i)
            val t = toothWeight(o) * w * k
            if (t <= 0f) continue
            val d = Colors.saturate(o, 0.25f)
            val c = Colors.argb(
                Colors.a(o),
                Colors.clamp8(Colors.r(d) + (255 - Colors.r(d)) * 0.28f),
                Colors.clamp8(Colors.g(d) + (255 - Colors.g(d)) * 0.28f),
                Colors.clamp8(Colors.b(d) + (255 - Colors.b(d)) * 0.30f),
            )
            put(buf, m, i, Colors.mix(o, c, t))
        }
    }

    enum class Blend { NORMAL, MULTIPLY, TINT }

    /** Blends a flat [color] through the mask. */
    fun applyColor(buf: PixelBuffer, m: RoiMask, color: Int, opacity: Float, mode: Blend, pivot: Float = 140f) {
        if (opacity <= 0f) return
        for (i in m.a.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = get(buf, m, i)
            val target = when (mode) {
                Blend.NORMAL -> color
                Blend.MULTIPLY -> Colors.multiply(o, color)
                Blend.TINT -> Colors.tintKeepLuma(o, color, pivot)
            }
            put(buf, m, i, Colors.mix(o, target, w * opacity))
        }
    }

    /** Darkens through the mask (used for eyebrow density). */
    fun darken(buf: PixelBuffer, m: RoiMask, amount: Float) {
        if (amount <= 0f) return
        for (i in m.a.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = get(buf, m, i)
            val k = 1f - amount * w
            put(buf, m, i, Colors.argb(Colors.a(o), (Colors.r(o) * k).toInt(), (Colors.g(o) * k).toInt(), (Colors.b(o) * k).toInt()))
        }
    }

    /**
     * Foundation: evens the low frequencies towards a shade matched to the wearer's own
     * brightness and keeps most of the pore texture.
     */
    fun foundation(buf: PixelBuffer, m: RoiMask, shade: Int, amount: Float, radius: Int) {
        if (amount <= 0f) return
        val src = extract(buf, m)
        val blur = Blur.boxRgb(src, m.width, m.height, max(1, radius))
        val k = min(amount, 1f) * 0.5f
        val shadeL = max(Colors.luma(shade), 1f)
        for (i in src.indices) {
            val w = m.a[i]
            if (w <= 0.003f) continue
            val o = src[i]; val bl = blur[i]
            val scale = min(Colors.luma(bl) * 1.03f / shadeL, 1.6f)
            val r = Colors.r(shade) * scale + (Colors.r(o) - Colors.r(bl)) * 0.8f
            val g = Colors.g(shade) * scale + (Colors.g(o) - Colors.g(bl)) * 0.8f
            val b = Colors.b(shade) * scale + (Colors.b(o) - Colors.b(bl)) * 0.8f
            put(buf, m, i, Colors.mix(o, Colors.rgb(r.toInt(), g.toInt(), b.toInt()), k * w))
        }
    }

    /** Mean luma of the image weighted by a low-res mask; used to pick the hair tint pivot. */
    fun meanLuma(buf: PixelBuffer, mask: LowResMask, threshold: Float = 0.5f): Float {
        var s = 0.0; var n = 0
        val stepX = max(1, buf.width / 160); val stepY = max(1, buf.height / 160)
        var y = 0
        while (y < buf.height) {
            var x = 0
            while (x < buf.width) {
                if (mask.sample((x + 0.5f) / buf.width, (y + 0.5f) / buf.height) > threshold) {
                    s += Colors.luma(buf.px[y * buf.width + x]); n++
                }
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 90f else (s / n).toFloat()
    }

    /** Re-colours everything covered by the (hair) segmentation mask. */
    fun tintByMask(buf: PixelBuffer, mask: LowResMask, color: Int, amount: Float) {
        if (amount <= 0f) return
        val pivot = max(55f, meanLuma(buf, mask) * 1.25f)
        val k = min(amount, 1f) * 0.9f
        val w = buf.width; val h = buf.height
        for (y in 0 until h) {
            val v = (y + 0.5f) / h
            for (x in 0 until w) {
                val conf = mask.sample((x + 0.5f) / w, v)
                if (conf < 0.25f) continue
                val t = Colors.smoothstep(0.3f, 0.7f, conf) * k
                val i = y * w + x
                buf.px[i] = Colors.mix(buf.px[i], Colors.tintKeepLuma(buf.px[i], color, pivot), t)
            }
        }
    }

    /**
     * Composites [background] behind the subject. [subject] returns subject coverage 0..1 for
     * normalised coordinates. [background] must have the same size as [buf].
     */
    fun compositeBackground(buf: PixelBuffer, background: IntArray, amount: Float, subject: (Float, Float) -> Float) {
        if (amount <= 0f) return
        val w = buf.width; val h = buf.height
        val k = min(amount, 1f)
        for (y in 0 until h) {
            val v = (y + 0.5f) / h
            for (x in 0 until w) {
                val bgW = (1f - subject((x + 0.5f) / w, v)) * k
                if (bgW <= 0.004f) continue
                val i = y * w + x
                buf.px[i] = Colors.mix(buf.px[i], background[i], bgW)
            }
        }
    }

    private fun get(buf: PixelBuffer, m: RoiMask, i: Int): Int =
        buf.px[(m.top + i / m.width) * buf.width + m.left + i % m.width]

    private fun put(buf: PixelBuffer, m: RoiMask, i: Int, c: Int) {
        buf.px[(m.top + i / m.width) * buf.width + m.left + i % m.width] = c
    }
}
