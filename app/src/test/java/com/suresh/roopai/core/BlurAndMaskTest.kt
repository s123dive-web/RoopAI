package com.suresh.roopai.core

import com.suresh.roopai.core.imaging.Blur
import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.IRect
import com.suresh.roopai.core.imaging.LowResMask
import com.suresh.roopai.core.imaging.RoiMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurAndMaskTest {
    @Test
    fun blurOfFlatImageIsUnchanged() {
        val c = Colors.rgb(40, 90, 200)
        val out = Blur.boxRgb(IntArray(20 * 10) { c }, 20, 10, 4)
        assertTrue(out.all { it == c })
    }

    @Test
    fun blurDoesNotMutateInputAndKeepsAlpha() {
        val src = IntArray(9 * 9) { Colors.argb(120, 0, 0, 0) }
        src[40] = Colors.argb(120, 255, 255, 255)
        val copy = src.copyOf()
        val out = Blur.boxRgb(src, 9, 9, 2)
        assertTrue(src.contentEquals(copy))
        assertTrue(out.all { Colors.a(it) == 120 })
    }

    @Test
    fun blurSpreadsAnImpulseSymmetrically() {
        val src = IntArray(21 * 21) { 0xFF000000.toInt() }
        src[10 * 21 + 10] = 0xFFFFFFFF.toInt()
        val out = Blur.boxRgb(src, 21, 21, 2, passes = 1)
        assertTrue(Colors.r(out[10 * 21 + 10]) < 255)
        assertEquals(Colors.r(out[10 * 21 + 8]), Colors.r(out[10 * 21 + 12]))
        assertEquals(Colors.r(out[8 * 21 + 10]), Colors.r(out[12 * 21 + 10]))
        assertEquals(0, Colors.r(out[0]))
    }

    @Test
    fun zeroRadiusIsACopy() {
        val src = IntArray(16) { it * 1000 }
        assertTrue(Blur.boxRgb(src, 4, 4, 0).contentEquals(src))
    }

    @Test
    fun floatBlurPreservesMeanOfInterior() {
        val w = 32; val h = 32
        val data = FloatArray(w * h)
        for (y in 12..19) for (x in 12..19) data[y * w + x] = 1f
        val before = data.sum()
        Blur.boxFloat(data, w, h, 3)
        assertEquals(before, data.sum(), 0.5f)
        assertTrue(data[16 * w + 16] > data[16 * w + 9])
    }

    @Test
    fun polygonFillCoversASquareExactly() {
        val m = RoiMask(0, 0, 20, 20)
        m.fillPolygon(floatArrayOf(5f, 5f, 15f, 5f, 15f, 15f, 5f, 15f))
        assertEquals(100f, m.a.sum(), 0.01f)
        assertEquals(1f, m.at(10, 10), 0f)
        assertEquals(0f, m.at(2, 2), 0f)
        assertEquals(0f, m.at(100, 100), 0f)
    }

    @Test
    fun polygonFillHandlesConcaveShapesAndOffsets() {
        // "L" shape in a mask that does not start at the origin
        val m = RoiMask(10, 10, 20, 20)
        m.fillPolygon(floatArrayOf(12f, 12f, 28f, 12f, 28f, 18f, 18f, 18f, 18f, 28f, 12f, 28f))
        assertEquals(1f, m.at(14, 25), 0f)
        assertEquals(1f, m.at(25, 14), 0f)
        assertEquals(0f, m.at(25, 25), 0f)
    }

    @Test
    fun polygonOutsideTheRoiIsIgnored() {
        val m = RoiMask(0, 0, 10, 10)
        m.fillPolygon(floatArrayOf(50f, 50f, 60f, 50f, 60f, 60f))
        assertEquals(0f, m.a.sum(), 0f)
        m.fillPolygon(floatArrayOf(1f, 1f, 2f, 2f)) // degenerate
        assertEquals(0f, m.a.sum(), 0f)
    }

    @Test
    fun ellipseIsFullInTheCentreAndSoftAtTheEdge() {
        val m = RoiMask(0, 0, 41, 41)
        m.fillEllipse(20.5f, 20.5f, 15f, 15f, 0f, soft = 0.5f)
        assertEquals(1f, m.at(20, 20), 0.01f)
        val mid = m.at(31, 20)
        assertTrue("edge is partially covered: $mid", mid > 0f && mid < 1f)
        assertEquals(0f, m.at(38, 20), 0f)
    }

    @Test
    fun hardEllipseAreaMatchesPiRSquared() {
        val m = RoiMask(0, 0, 101, 101)
        m.fillEllipse(50.5f, 50.5f, 30f, 20f)
        assertEquals(Math.PI * 30 * 20, m.a.sum().toDouble(), 40.0)
    }

    @Test
    fun strokeCoversTheLineAndRespectsWidth() {
        val m = RoiMask(0, 0, 40, 20)
        m.strokePolyline(floatArrayOf(5f, 10f, 35f, 10f), 2f)
        assertEquals(1f, m.at(20, 10), 0.01f)
        assertEquals(0f, m.at(20, 15), 0f)
        assertEquals(0f, m.at(20, 2), 0f)
    }

    @Test
    fun strokeTaperNarrowsTheEnd() {
        val m = RoiMask(0, 0, 60, 20)
        m.strokePolyline(floatArrayOf(5f, 10f, 55f, 10f), 4f) { t -> 1f - t }
        assertTrue(m.at(8, 13) > 0.5f)
        assertEquals(0f, m.at(52, 13), 0f)
    }

    @Test
    fun subtractAndFeather() {
        val a = RoiMask(0, 0, 20, 20).fillPolygon(floatArrayOf(0f, 0f, 20f, 0f, 20f, 20f, 0f, 20f))
        val hole = RoiMask(5, 5, 10, 10).fillPolygon(floatArrayOf(5f, 5f, 15f, 5f, 15f, 15f, 5f, 15f))
        a.subtract(hole)
        assertEquals(0f, a.at(10, 10), 0f)
        assertEquals(1f, a.at(2, 2), 0f)
        a.feather(2)
        assertTrue(a.at(10, 10) in 0f..0.5f)
        assertTrue(a.at(5, 10) > 0f && a.at(5, 10) < 1f)
    }

    @Test
    fun aroundClipsToTheImage() {
        val m = RoiMask.around(floatArrayOf(-5f, -5f, 10f, 10f), 4f, 12, 12)
        assertNotNull(m)
        assertEquals(0, m!!.left); assertEquals(0, m.top); assertEquals(12, m.width); assertEquals(12, m.height)
        assertNull(RoiMask.around(floatArrayOf(500f, 500f), 2f, 12, 12))
    }

    @Test
    fun rectClipAndEmpty() {
        assertTrue(IRect(5, 5, 5, 9).isEmpty)
        assertEquals(IRect(0, 0, 10, 10), IRect(-4, -4, 40, 40).clip(10, 10))
    }

    @Test
    fun lowResMaskSamplesBilinearly() {
        val m = LowResMask(2, 1, floatArrayOf(0f, 1f))
        assertEquals(0f, m.sample(0f, 0.5f), 1e-4f)
        assertEquals(1f, m.sample(1f, 0.5f), 1e-4f)
        assertEquals(0.5f, m.sample(0.5f, 0.5f), 1e-4f)
        assertEquals(0.5f, m.mean(), 1e-4f)
    }
}
