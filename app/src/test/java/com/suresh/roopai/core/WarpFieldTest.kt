package com.suresh.roopai.core

import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.IRect
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.core.imaging.WarpField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpFieldTest {
    private fun dot(w: Int, h: Int, cx: Int, cy: Int, r: Int): PixelBuffer {
        val b = PixelBuffer.filled(w, h, 0xFF000000.toInt())
        for (y in 0 until h) for (x in 0 until w) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) b[x, y] = 0xFFFFFFFF.toInt()
        }
        return b
    }

    private fun centroidX(b: PixelBuffer): Float {
        var s = 0f; var n = 0f
        for (y in 0 until b.height) for (x in 0 until b.width) { val v = Colors.r(b[x, y]) / 255f; s += x * v; n += v }
        return s / n
    }

    private fun mass(b: PixelBuffer): Float = b.px.sumOf { Colors.r(it) / 255.0 }.toFloat()

    @Test
    fun emptyFieldIsIdentityAndLeavesPixelsAlone() {
        val b = dot(64, 64, 32, 32, 6)
        val before = b.px.copyOf()
        val f = WarpField(IRect(0, 0, 64, 64), 4)
        assertTrue(f.isIdentity)
        f.apply(b)
        assertTrue(b.px.contentEquals(before))
    }

    @Test
    fun translateMovesContentInTheRequestedDirection() {
        val b = dot(96, 96, 48, 48, 5)
        val x0 = centroidX(b)
        val f = WarpField(IRect(0, 0, 96, 96), 2)
        f.addTranslate(48f, 48f, 8f, 0f, 30f)
        assertFalse(f.isIdentity)
        f.apply(b)
        val moved = centroidX(b) - x0
        assertTrue("moved right by roughly the requested amount: $moved", moved > 5f && moved < 9f)
    }

    @Test
    fun displacementIsZeroOutsideTheKernelRadius() {
        val f = WarpField(IRect(0, 0, 200, 200), 4)
        f.addTranslate(100f, 100f, 10f, 5f, 20f)
        val (dx, dy) = f.displacementAt(100f, 100f)
        assertEquals(-10f, dx, 0.5f); assertEquals(-5f, dy, 0.3f)
        val far = f.displacementAt(150f, 150f)
        assertEquals(0f, far.first, 0f); assertEquals(0f, far.second, 0f)
    }

    @Test
    fun pixelsOutsideTheRegionNeverChange() {
        val b = PixelBuffer(80, 80, IntArray(80 * 80) { 0xFF000000.toInt() or (it * 37 and 0xFFFFFF) })
        val before = b.px.copyOf()
        val f = WarpField(IRect(20, 20, 60, 60), 2)
        f.addTranslate(40f, 40f, 6f, -4f, 18f)
        f.apply(b)
        for (y in 0 until 80) for (x in 0 until 80) {
            if (x < 20 || x >= 60 || y < 20 || y >= 60) assertEquals(before[y * 80 + x], b[x, y])
        }
        // the region border itself is pinned too
        for (x in 20 until 60) assertEquals(before[20 * 80 + x], b[x, 20])
    }

    @Test
    fun positiveScaleMagnifiesAndNegativeShrinks() {
        val big = dot(96, 96, 48, 48, 8)
        val m0 = mass(big)
        WarpField(IRect(0, 0, 96, 96), 2).apply { addScale(48f, 48f, 30f, 0.3f) }.apply(big)
        assertTrue(mass(big) > m0 * 1.15f)
        val small = dot(96, 96, 48, 48, 8)
        WarpField(IRect(0, 0, 96, 96), 2).apply { addScale(48f, 48f, 30f, -0.3f) }.apply(small)
        assertTrue(mass(small) < m0 * 0.9f)
    }

    @Test
    fun scaleAlongOnlyStretchesThatAxis() {
        val f = WarpField(IRect(0, 0, 200, 200), 4)
        f.addScaleAlong(100f, 100f, 60f, 0.4f, 0f, 1f)
        val side = f.displacementAt(120f, 100f)
        assertEquals(0f, side.first, 1e-3f); assertEquals(0f, side.second, 1e-3f)
        val below = f.displacementAt(100f, 120f)
        assertEquals(0f, below.first, 1e-3f)
        assertTrue("samples from nearer the centre", below.second < -2f)
    }

    @Test
    fun bilinearInterpolatesBetweenNeighbours() {
        val src = intArrayOf(Colors.rgb(0, 0, 0), Colors.rgb(100, 200, 50))
        val c = WarpField.bilinear(src, 2, 1, 0.5f, 0f)
        assertEquals(50, Colors.r(c)); assertEquals(100, Colors.g(c)); assertEquals(25, Colors.b(c))
        assertEquals(src[1], WarpField.bilinear(src, 2, 1, 5f, 5f))
    }
}
