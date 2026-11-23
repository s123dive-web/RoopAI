package com.suresh.roopai.core

import com.suresh.roopai.core.imaging.ColorFilters
import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.Geometry
import com.suresh.roopai.core.imaging.IRect
import com.suresh.roopai.core.imaging.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersAndGeometryTest {
    private fun seq(w: Int, h: Int) = PixelBuffer(w, h, IntArray(w * h) { 0xFF000000.toInt() or it })

    @Test
    fun identityMatrixChangesNothing() {
        val b = seq(5, 5); val before = b.px.copyOf()
        ColorFilters.apply(b, ColorFilters.identity())
        assertTrue(b.px.contentEquals(before))
        assertTrue(ColorFilters.isIdentity(ColorFilters.adjustments(0f, 0f, 0f, 0f)))
    }

    @Test
    fun everyListedFilterExistsAndUnknownIsNull() {
        for (name in ColorFilters.FILTERS) assertNotNull(name, ColorFilters.filter(name))
        assertNull(ColorFilters.filter("nope"))
    }

    @Test
    fun monoProducesGreyAndStrengthZeroIsIdentity() {
        val b = PixelBuffer.filled(2, 2, Colors.rgb(200, 40, 90))
        ColorFilters.apply(b, ColorFilters.filter("mono")!!)
        val c = b[0, 0]
        assertEquals(Colors.r(c), Colors.g(c)); assertEquals(Colors.g(c), Colors.b(c))
        assertTrue(ColorFilters.isIdentity(ColorFilters.strength(ColorFilters.filter("sepia")!!, 0f)))
    }

    @Test
    fun adjustmentsMoveInTheExpectedDirection() {
        val base = Colors.rgb(120, 110, 100)
        fun run(m: FloatArray) = PixelBuffer.filled(1, 1, base).also { ColorFilters.apply(it, m) }[0, 0]
        assertTrue(Colors.luma(run(ColorFilters.adjustments(0.5f, 0f, 0f, 0f))) > Colors.luma(base))
        assertTrue(Colors.luma(run(ColorFilters.adjustments(-0.5f, 0f, 0f, 0f))) < Colors.luma(base))
        assertTrue(Colors.saturation(run(ColorFilters.adjustments(0f, 0f, 0.8f, 0f))) > Colors.saturation(base))
        assertEquals(0f, Colors.saturation(run(ColorFilters.adjustments(0f, 0f, -1f, 0f))), 0.02f)
        val warm = run(ColorFilters.adjustments(0f, 0f, 0f, 1f))
        assertTrue(Colors.r(warm) > Colors.r(base) && Colors.b(warm) < Colors.b(base))
        val hi = Colors.rgb(200, 200, 200); val lo = Colors.rgb(50, 50, 50)
        val m = ColorFilters.adjustments(0f, 1f, 0f, 0f)
        val bh = PixelBuffer.filled(1, 1, hi).also { ColorFilters.apply(it, m) }[0, 0]
        val bl = PixelBuffer.filled(1, 1, lo).also { ColorFilters.apply(it, m) }[0, 0]
        assertTrue(Colors.r(bh) > 200 && Colors.r(bl) < 50)
    }

    @Test
    fun concatAppliesRightHandSideFirst() {
        val m = ColorFilters.concat(ColorFilters.brightness(10f), ColorFilters.gains(2f, 2f, 2f))
        val b = PixelBuffer.filled(1, 1, Colors.rgb(10, 20, 30))
        ColorFilters.apply(b, m)
        assertEquals(Colors.rgb(30, 50, 70), b[0, 0])
    }

    @Test
    fun rotate90FourTimesIsIdentityAndSwapsSize() {
        val b = seq(3, 2)
        val r1 = Geometry.rotate90(b, 1)
        assertEquals(2, r1.width); assertEquals(3, r1.height)
        // top-left goes to top-right when rotating clockwise
        assertEquals(b[0, 0], r1[1, 0])
        assertEquals(b[2, 1], r1[0, 2])
        var r = b
        repeat(4) { r = Geometry.rotate90(r, 1) }
        assertTrue(r.px.contentEquals(b.px))
        assertTrue(Geometry.rotate90(b, -1).px.contentEquals(Geometry.rotate90(b, 3).px))
        assertSame(b, Geometry.rotate90(b, 4))
    }

    @Test
    fun flipTwiceIsIdentity() {
        val b = seq(4, 3)
        val h = Geometry.flip(b, horizontal = true, vertical = false)
        assertEquals(b[0, 0], h[3, 0])
        val v = Geometry.flip(b, horizontal = false, vertical = true)
        assertEquals(b[0, 0], v[0, 2])
        assertTrue(Geometry.flip(h, true, false).px.contentEquals(b.px))
        assertSame(b, Geometry.flip(b, false, false))
    }

    @Test
    fun straightenShrinksAndNeverSamplesOutside() {
        val b = PixelBuffer.filled(200, 100, 0xFF336699.toInt())
        val s = Geometry.straighten(b, 10f)
        assertTrue(s.width < 200 && s.height < 100)
        assertEquals(2.0, s.width.toDouble() / s.height, 0.05)
        assertTrue(s.px.all { it == 0xFF336699.toInt() })
        assertSame(b, Geometry.straighten(b, 0f))
        assertEquals(1f, Geometry.straightenScale(100, 100, 0f), 0f)
        assertEquals(Geometry.straightenScale(300, 200, 7f), Geometry.straightenScale(300, 200, -7f), 1e-6f)
    }

    @Test
    fun cropRectIsOrderedClampedAndNonEmpty() {
        assertEquals(IRect(10, 20, 90, 80), Geometry.cropRect(100, 100, 0.1f, 0.2f, 0.9f, 0.8f))
        assertEquals(IRect(10, 20, 90, 80), Geometry.cropRect(100, 100, 0.9f, 0.8f, 0.1f, 0.2f))
        assertEquals(IRect(0, 0, 100, 100), Geometry.cropRect(100, 100, -3f, -3f, 9f, 9f))
        val tiny = Geometry.cropRect(100, 100, 0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue(tiny.width >= 8 && tiny.height >= 8)
        val edge = Geometry.cropRect(100, 100, 1f, 1f, 1f, 1f)
        assertTrue(edge.right <= 100 && edge.bottom <= 100 && edge.width >= 8)
        val small = Geometry.cropRect(4, 4, 0.4f, 0.4f, 0.5f, 0.5f)
        assertTrue(small.width in 1..4)
    }

    @Test
    fun cropCopiesTheRightPixels() {
        val b = seq(10, 10)
        val c = Geometry.crop(b, IRect(2, 3, 6, 5))
        assertEquals(4, c.width); assertEquals(2, c.height)
        assertEquals(b[2, 3], c[0, 0]); assertEquals(b[5, 4], c[3, 1])
        assertSame(b, Geometry.crop(b, IRect(0, 0, 10, 10)))
    }

    @Test
    fun centeredAspectCrop() {
        val sq = Geometry.centeredAspectCrop(200, 100, 1f)
        assertEquals(0.25f, sq[0], 1e-4f); assertEquals(0.75f, sq[2], 1e-4f); assertEquals(0f, sq[1], 0f); assertEquals(1f, sq[3], 0f)
        val wide = Geometry.centeredAspectCrop(100, 100, 2f)
        assertEquals(0.25f, wide[1], 1e-4f); assertEquals(0.75f, wide[3], 1e-4f)
    }

    @Test
    fun fitSizeRespectsLongSideAndPixelBudget() {
        assertEquals(1000 to 500, Geometry.fitSize(4000, 2000, 1000))
        assertEquals(400 to 300, Geometry.fitSize(400, 300, 1000))
        // 50 MP photo with a 12 MP budget
        val (w, h) = Geometry.fitSize(8160, 6120, 100_000, 12_000_000)
        assertTrue(w.toLong() * h <= 12_000_000)
        assertEquals(8160.0 / 6120, w.toDouble() / h, 0.01)
    }

    @Test
    fun sampleSizeIsLargestPowerOfTwoThatStillCoversTheRequest() {
        assertEquals(1, Geometry.sampleSize(1000, 1000, 1000, 1000))
        assertEquals(4, Geometry.sampleSize(8160, 6120, 1280, 960))
        assertEquals(8, Geometry.sampleSize(8160, 6120, 1000, 750))
        assertEquals(1, Geometry.sampleSize(100, 100, 4000, 4000))
    }
}
