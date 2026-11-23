package com.suresh.roopai.core.imaging

/**
 * Separable running-sum box blur. Three passes approximate a gaussian; cost is O(pixels)
 * regardless of radius, which keeps live preview fast and export predictable.
 */
object Blur {
    /** Blurs RGB of [src] (w*h ARGB) and returns a new array. Alpha is copied. */
    fun boxRgb(src: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3): IntArray {
        if (radius <= 0 || w <= 1 && h <= 1) return src.copyOf()
        val a = src.copyOf()
        val b = IntArray(src.size)
        repeat(passes) {
            horizontal(a, b, w, h, radius)
            vertical(b, a, w, h, radius)
        }
        // restore alpha
        for (i in a.indices) a[i] = (a[i] and 0x00FFFFFF) or (src[i] and -0x1000000)
        return a
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val out = src[row + (x - r).coerceAtLeast(0)]
                val inn = src[row + (x + r + 1).coerceAtMost(w - 1)]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val win = 2 * r + 1
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val out = src[(y - r).coerceAtLeast(0) * w + x]
                val inn = src[(y + r + 1).coerceAtMost(h - 1) * w + x]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    /** In-place box blur of a float plane (used to feather masks). */
    fun boxFloat(data: FloatArray, w: Int, h: Int, radius: Int, passes: Int = 2) {
        if (radius <= 0) return
        val tmp = FloatArray(data.size)
        val win = (2 * radius + 1).toFloat()
        repeat(passes) {
            for (y in 0 until h) {
                val row = y * w
                var s = 0f
                for (i in -radius..radius) s += data[row + i.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[row + x] = s / win
                    s += data[row + (x + radius + 1).coerceAtMost(w - 1)] -
                        data[row + (x - radius).coerceAtLeast(0)]
                }
            }
            for (x in 0 until w) {
                var s = 0f
                for (i in -radius..radius) s += tmp[i.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    data[y * w + x] = s / win
                    s += tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x] -
                        tmp[(y - radius).coerceAtLeast(0) * w + x]
                }
            }
        }
    }
}
