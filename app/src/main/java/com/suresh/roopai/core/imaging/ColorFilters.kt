package com.suresh.roopai.core.imaging

/** Colour filters and global adjustments expressed as 3x4 colour matrices. */
object ColorFilters {
    val FILTERS: List<String> = listOf(
        "mono", "noir", "sepia", "warm", "cool", "vivid", "fade", "rose", "golden", "teal",
    )

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun identity(): FloatArray = IDENTITY.copyOf()

    /** c = a after b (apply [b] first). */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(12)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                o[row * 4 + col] = a[row * 4] * b[col] + a[row * 4 + 1] * b[4 + col] + a[row * 4 + 2] * b[8 + col]
            }
            o[row * 4 + 3] = a[row * 4] * b[3] + a[row * 4 + 1] * b[7] + a[row * 4 + 2] * b[11] + a[row * 4 + 3]
        }
        return o
    }

    fun saturation(s: Float): FloatArray {
        val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
        return floatArrayOf(
            lr + (1 - lr) * s, lg - lg * s, lb - lb * s, 0f,
            lr - lr * s, lg + (1 - lg) * s, lb - lb * s, 0f,
            lr - lr * s, lg - lg * s, lb + (1 - lb) * s, 0f,
        )
    }

    fun contrast(c: Float): FloatArray {
        val o = 127.5f * (1 - c)
        return floatArrayOf(c, 0f, 0f, o, 0f, c, 0f, o, 0f, 0f, c, o)
    }

    fun brightness(offset: Float): FloatArray =
        floatArrayOf(1f, 0f, 0f, offset, 0f, 1f, 0f, offset, 0f, 0f, 1f, offset)

    fun gains(r: Float, g: Float, b: Float): FloatArray =
        floatArrayOf(r, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, b, 0f)

    /** Matrix for a named filter at full strength; null for unknown names. */
    fun filter(name: String): FloatArray? = when (name) {
        "mono" -> saturation(0f)
        "noir" -> concat(contrast(1.35f), saturation(0f))
        "sepia" -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f,
            0.349f, 0.686f, 0.168f, 0f,
            0.272f, 0.534f, 0.131f, 0f,
        )
        "warm" -> gains(1.10f, 1.02f, 0.88f)
        "cool" -> gains(0.90f, 1.0f, 1.12f)
        "vivid" -> concat(contrast(1.12f), saturation(1.4f))
        "fade" -> concat(brightness(22f), concat(contrast(0.82f), saturation(0.8f)))
        "rose" -> concat(gains(1.08f, 0.96f, 1.02f), brightness(6f))
        "golden" -> concat(gains(1.12f, 1.05f, 0.82f), contrast(1.05f))
        "teal" -> concat(gains(0.92f, 1.04f, 1.08f), saturation(1.15f))
        else -> null
    }

    /** Linear interpolation between identity and [m]. */
    fun strength(m: FloatArray, t: Float): FloatArray =
        FloatArray(12) { IDENTITY[it] + (m[it] - IDENTITY[it]) * t }

    /** Combined matrix for the four adjustment sliders, each in -1..1. */
    fun adjustments(brightness: Float, contrast: Float, saturation: Float, warmth: Float): FloatArray {
        var m = identity()
        if (warmth != 0f) m = concat(gains(1f + 0.18f * warmth, 1f + 0.03f * warmth, 1f - 0.18f * warmth), m)
        if (saturation != 0f) m = concat(saturation(1f + saturation), m)
        if (contrast != 0f) m = concat(contrast(1f + 0.5f * contrast), m)
        if (brightness != 0f) m = concat(brightness(70f * brightness), m)
        return m
    }

    fun isIdentity(m: FloatArray): Boolean = m.indices.all { kotlin.math.abs(m[it] - IDENTITY[it]) < 1e-5f }

    fun apply(buf: PixelBuffer, m: FloatArray) {
        if (isIdentity(m)) return
        val px = buf.px
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            px[i] = (c and -0x1000000) or
                (Colors.clamp8(m[0] * r + m[1] * g + m[2] * b + m[3]) shl 16) or
                (Colors.clamp8(m[4] * r + m[5] * g + m[6] * b + m[7]) shl 8) or
                Colors.clamp8(m[8] * r + m[9] * g + m[10] * b + m[11])
        }
    }
}
