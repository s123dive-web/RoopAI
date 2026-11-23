package com.suresh.roopai.core.imaging

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A float coverage mask (0..1) that only covers a rectangle of the image, so face-local edits
 * never allocate full-image planes. Coordinates passed to the drawing functions are image-space.
 */
class RoiMask(val left: Int, val top: Int, val width: Int, val height: Int) {
    val a = FloatArray(width * height)
    val right get() = left + width
    val bottom get() = top + height

    fun at(x: Int, y: Int): Float {
        val lx = x - left
        val ly = y - top
        return if (lx < 0 || ly < 0 || lx >= width || ly >= height) 0f else a[ly * width + lx]
    }

    /** Even-odd scanline fill of a polygon given as x0,y0,x1,y1,... Result is max-combined. */
    fun fillPolygon(pts: FloatArray, value: Float = 1f): RoiMask {
        val n = pts.size / 2
        if (n < 3) return this
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minY = min(minY, pts[2 * i + 1]); maxY = max(maxY, pts[2 * i + 1]) }
        val y0 = max(top, floor(minY).toInt())
        val y1 = min(bottom - 1, ceil(maxY).toInt())
        val xs = FloatArray(n)
        for (y in y0..y1) {
            val sy = y + 0.5f
            var cnt = 0
            var j = n - 1
            for (i in 0 until n) {
                val yi = pts[2 * i + 1]
                val yj = pts[2 * j + 1]
                if ((yi <= sy && yj > sy) || (yj <= sy && yi > sy)) {
                    xs[cnt++] = pts[2 * i] + (sy - yi) / (yj - yi) * (pts[2 * j] - pts[2 * i])
                }
                j = i
            }
            if (cnt < 2) continue
            java.util.Arrays.sort(xs, 0, cnt)
            var k = 0
            while (k + 1 < cnt) {
                val xa = max(left, ceil(xs[k] - 0.5f).toInt())
                val xb = min(right - 1, floor(xs[k + 1] - 0.5f).toInt())
                val row = (y - top) * width - left
                for (x in xa..xb) if (a[row + x] < value) a[row + x] = value
                k += 2
            }
        }
        return this
    }

    /** Filled ellipse with radii [rx],[ry], rotated by [angle] radians, soft edge of [soft] (0..1 of radius). */
    fun fillEllipse(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float = 0f, soft: Float = 0f, value: Float = 1f): RoiMask {
        if (rx <= 0f || ry <= 0f) return this
        val rm = max(rx, ry)
        val x0 = max(left, floor(cx - rm).toInt()); val x1 = min(right - 1, ceil(cx + rm).toInt())
        val y0 = max(top, floor(cy - rm).toInt()); val y1 = min(bottom - 1, ceil(cy + rm).toInt())
        val ca = cos(angle); val sa = sin(angle)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            val u = (dx * ca + dy * sa) / rx
            val v = (-dx * sa + dy * ca) / ry
            val d = sqrt(u * u + v * v)
            if (d >= 1f) continue
            val w = if (soft <= 0f) 1f else 1f - Colors.smoothstep(1f - soft, 1f, d)
            val i = (y - top) * width + (x - left)
            val nv = w * value
            if (a[i] < nv) a[i] = nv
        }
        return this
    }

    /**
     * Anti-aliased polyline stroke. [halfWidth] may vary along the line via [taper]
     * (t in 0..1 along the polyline -> multiplier).
     */
    fun strokePolyline(pts: FloatArray, halfWidth: Float, value: Float = 1f, taper: ((Float) -> Float)? = null): RoiMask {
        val n = pts.size / 2
        if (n < 2 || halfWidth <= 0f) return this
        var total = 0f
        for (i in 0 until n - 1) total += dist(pts[2 * i], pts[2 * i + 1], pts[2 * i + 2], pts[2 * i + 3])
        if (total <= 0f) return this
        var acc = 0f
        for (i in 0 until n - 1) {
            val ax = pts[2 * i]; val ay = pts[2 * i + 1]; val bx = pts[2 * i + 2]; val by = pts[2 * i + 3]
            val len = dist(ax, ay, bx, by)
            val pad = halfWidth + 1.5f
            val x0 = max(left, floor(min(ax, bx) - pad).toInt()); val x1 = min(right - 1, ceil(max(ax, bx) + pad).toInt())
            val y0 = max(top, floor(min(ay, by) - pad).toInt()); val y1 = min(bottom - 1, ceil(max(ay, by) + pad).toInt())
            val vx = bx - ax; val vy = by - ay
            val l2 = max(vx * vx + vy * vy, 1e-6f)
            for (y in y0..y1) for (x in x0..x1) {
                val px = x + 0.5f - ax; val py = y + 0.5f - ay
                val t = ((px * vx + py * vy) / l2).coerceIn(0f, 1f)
                val dx = px - vx * t; val dy = py - vy * t
                val d = sqrt(dx * dx + dy * dy)
                val hw = if (taper == null) halfWidth else halfWidth * taper((acc + len * t) / total)
                val cov = (hw + 0.5f - d).coerceIn(0f, 1f) * value
                if (cov > 0f) {
                    val idx = (y - top) * width + (x - left)
                    if (a[idx] < cov) a[idx] = cov
                }
            }
            acc += len
        }
        return this
    }

    fun feather(radius: Int): RoiMask {
        Blur.boxFloat(a, width, height, radius)
        return this
    }

    /** this = this * (1 - other), other sampled in image space. */
    fun subtract(other: RoiMask): RoiMask {
        for (y in 0 until height) for (x in 0 until width) {
            val o = other.at(x + left, y + top)
            if (o > 0f) a[y * width + x] *= (1f - o)
        }
        return this
    }

    fun scale(k: Float): RoiMask {
        for (i in a.indices) a[i] *= k
        return this
    }

    fun maxValue(): Float = a.maxOrNull() ?: 0f

    companion object {
        /** Creates a mask covering the bounding box of [pts] grown by [pad], clipped to the image. */
        fun around(pts: FloatArray, pad: Float, imgW: Int, imgH: Int): RoiMask? {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until pts.size / 2) {
                minX = min(minX, pts[2 * i]); maxX = max(maxX, pts[2 * i])
                minY = min(minY, pts[2 * i + 1]); maxY = max(maxY, pts[2 * i + 1])
            }
            val r = IRect(
                floor(minX - pad).toInt(), floor(minY - pad).toInt(),
                ceil(maxX + pad).toInt() + 1, ceil(maxY + pad).toInt() + 1,
            ).clip(imgW, imgH)
            return if (r.isEmpty) null else RoiMask(r.left, r.top, r.width, r.height)
        }

        fun of(rect: IRect): RoiMask = RoiMask(rect.left, rect.top, rect.width, rect.height)

        private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            return sqrt(dx * dx + dy * dy)
        }
    }
}

/** Low resolution plane (e.g. a 256x256 segmentation confidence map) sampled bilinearly. */
class LowResMask(val width: Int, val height: Int, val data: FloatArray) {
    init { require(data.size == width * height) }

    /** [u],[v] in 0..1 image-normalised coordinates. */
    fun sample(u: Float, v: Float): Float {
        val fx = (u * width - 0.5f).coerceIn(0f, width - 1f)
        val fy = (v * height - 0.5f).coerceIn(0f, height - 1f)
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
        val tx = fx - x0; val ty = fy - y0
        val top = data[y0 * width + x0] * (1 - tx) + data[y0 * width + x1] * tx
        val bot = data[y1 * width + x0] * (1 - tx) + data[y1 * width + x1] * tx
        return top * (1 - ty) + bot * ty
    }

    fun mean(): Float = if (data.isEmpty()) 0f else data.sum() / data.size
}
