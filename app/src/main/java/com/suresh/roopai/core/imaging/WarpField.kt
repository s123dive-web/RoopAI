package com.suresh.roopai.core.imaging

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Backward-mapping displacement field on a coarse grid (mesh warp). Local "liquify" kernels are
 * accumulated into the grid, then [apply] resamples the region bilinearly.
 * Displacements are forced to zero at the region border so the warp never tears.
 */
class WarpField(val region: IRect, val step: Int) {
    val gw = region.width / step + 2
    val gh = region.height / step + 2
    private val dx = FloatArray(gw * gh)
    private val dy = FloatArray(gw * gh)
    var isIdentity = true
        private set

    private inline fun forNodes(cx: Float, cy: Float, r: Float, f: (idx: Int, nx: Float, ny: Float, w: Float) -> Unit) {
        if (r <= 0f) return
        val gx0 = max(0, ((cx - r - region.left) / step).toInt())
        val gx1 = min(gw - 1, ((cx + r - region.left) / step).toInt() + 1)
        val gy0 = max(0, ((cy - r - region.top) / step).toInt())
        val gy1 = min(gh - 1, ((cy + r - region.top) / step).toInt() + 1)
        for (gy in gy0..gy1) for (gx in gx0..gx1) {
            val nx = (region.left + gx * step).toFloat()
            val ny = (region.top + gy * step).toFloat()
            val ddx = nx - cx; val ddy = ny - cy
            val t2 = (ddx * ddx + ddy * ddy) / (r * r)
            if (t2 >= 1f) continue
            val w = (1f - t2) * (1f - t2)
            f(gy * gw + gx, nx, ny, w)
        }
    }

    /** Moves content near ([cx],[cy]) by ([mx],[my]) pixels with smooth falloff over [radius]. */
    fun addTranslate(cx: Float, cy: Float, mx: Float, my: Float, radius: Float) {
        if (mx == 0f && my == 0f) return
        isIdentity = false
        forNodes(cx, cy, radius) { i, _, _, w ->
            dx[i] -= mx * w
            dy[i] -= my * w
        }
    }

    /** Magnifies (k > 0) or shrinks (k < 0) content around the centre. |k| should stay below ~0.6. */
    fun addScale(cx: Float, cy: Float, radius: Float, k: Float) {
        if (k == 0f) return
        isIdentity = false
        forNodes(cx, cy, radius) { i, nx, ny, w ->
            dx[i] -= (nx - cx) * k * w
            dy[i] -= (ny - cy) * k * w
        }
    }

    /** Like [addScale] but only along the unit axis ([ax],[ay]). */
    fun addScaleAlong(cx: Float, cy: Float, radius: Float, k: Float, ax: Float, ay: Float) {
        if (k == 0f) return
        isIdentity = false
        forNodes(cx, cy, radius) { i, nx, ny, w ->
            val proj = (nx - cx) * ax + (ny - cy) * ay
            dx[i] -= proj * ax * k * w
            dy[i] -= proj * ay * k * w
        }
    }

    /** Displacement (source offset) at an image pixel; exposed for tests. */
    fun displacementAt(x: Float, y: Float): Pair<Float, Float> {
        val fx = ((x - region.left) / step).coerceIn(0f, gw - 1.001f)
        val fy = ((y - region.top) / step).coerceIn(0f, gh - 1.001f)
        val gx = fx.toInt(); val gy = fy.toInt()
        val tx = fx - gx; val ty = fy - gy
        val i = gy * gw + gx
        val ox = (dx[i] * (1 - tx) + dx[i + 1] * tx) * (1 - ty) + (dx[i + gw] * (1 - tx) + dx[i + gw + 1] * tx) * ty
        val oy = (dy[i] * (1 - tx) + dy[i + 1] * tx) * (1 - ty) + (dy[i + gw] * (1 - tx) + dy[i + gw + 1] * tx) * ty
        return ox to oy
    }

    /** Resamples [buf] inside the region. */
    fun apply(buf: PixelBuffer) {
        if (isIdentity) return
        val r = region.clip(buf.width, buf.height)
        if (r.isEmpty) return
        val w = buf.width
        val src = IntArray(r.width * r.height)
        for (y in 0 until r.height) System.arraycopy(buf.px, (r.top + y) * w + r.left, src, y * r.width, r.width)
        val edge = max(1f, min(r.width, r.height) * 0.08f)
        for (y in r.top until r.bottom) {
            for (x in r.left until r.right) {
                var (ox, oy) = displacementAt(x.toFloat(), y.toFloat())
                if (ox == 0f && oy == 0f) continue
                // window to guarantee continuity at the region border
                val border = min(min(x - r.left, r.right - 1 - x), min(y - r.top, r.bottom - 1 - y)).toFloat()
                if (border < edge) { val k = border / edge; ox *= k; oy *= k }
                val sx = (x + ox - r.left).coerceIn(0f, r.width - 1f)
                val sy = (y + oy - r.top).coerceIn(0f, r.height - 1f)
                buf.px[y * w + x] = bilinear(src, r.width, r.height, sx, sy)
            }
        }
    }

    companion object {
        fun bilinear(src: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
            val x0 = fx.toInt().coerceIn(0, w - 1); val y0 = fy.toInt().coerceIn(0, h - 1)
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val tx = fx - x0; val ty = fy - y0
            val c00 = src[y0 * w + x0]; val c10 = src[y0 * w + x1]
            val c01 = src[y1 * w + x0]; val c11 = src[y1 * w + x1]
            fun ch(shift: Int): Int {
                val a = ((c00 shr shift) and 0xFF) * (1 - tx) + ((c10 shr shift) and 0xFF) * tx
                val b = ((c01 shr shift) and 0xFF) * (1 - tx) + ((c11 shr shift) and 0xFF) * tx
                return (a * (1 - ty) + b * ty + 0.5f).toInt().coerceIn(0, 255)
            }
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun length(x: Float, y: Float) = sqrt(x * x + y * y)
    }
}
