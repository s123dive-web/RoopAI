package com.suresh.roopai.core

import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.LowResMask
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.core.imaging.Retouch
import com.suresh.roopai.core.imaging.RoiMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RetouchTest {
    private val skin = Colors.rgb(200, 150, 125)

    private fun full(w: Int, h: Int) = RoiMask(0, 0, w, h).also { it.a.fill(1f) }

    private fun noisySkin(w: Int, h: Int): PixelBuffer {
        val rnd = java.util.Random(7)
        return PixelBuffer(w, h, IntArray(w * h) {
            val n = rnd.nextInt(17) - 8
            Colors.rgb(200 + n, 150 + n, 125 + n)
        })
    }

    private fun variance(b: PixelBuffer): Double {
        val l = b.px.map { Colors.luma(it).toDouble() }
        val m = l.average()
        return l.sumOf { (it - m) * (it - m) } / l.size
    }

    @Test
    fun zeroAmountNeverChangesPixels() {
        val b = noisySkin(24, 24); val before = b.px.copyOf(); val m = full(24, 24)
        Retouch.smooth(b, m, 0f, 3)
        Retouch.blemish(b, m, 0f, 1, 4)
        Retouch.pullLowFrequency(b, m, 0f, 4, skin)
        Retouch.softenLines(b, m, 0f, 2)
        Retouch.brightenEyes(b, m, 0f)
        Retouch.whitenTeeth(b, m, 0f)
        Retouch.applyColor(b, m, 0xFFFF0000.toInt(), 0f, Retouch.Blend.NORMAL)
        Retouch.darken(b, m, 0f)
        Retouch.foundation(b, m, skin, 0f, 3)
        assertTrue(b.px.contentEquals(before))
    }

    @Test
    fun emptyMaskNeverChangesPixels() {
        val b = noisySkin(24, 24); val before = b.px.copyOf(); val m = RoiMask(0, 0, 24, 24)
        Retouch.smooth(b, m, 1f, 3)
        Retouch.applyColor(b, m, 0xFFFF0000.toInt(), 1f, Retouch.Blend.NORMAL)
        Retouch.whitenTeeth(b, m, 1f)
        assertTrue(b.px.contentEquals(before))
        assertNull(Retouch.meanColor(b, m))
    }

    @Test
    fun smoothingReducesNoiseButKeepsAStrongEdge() {
        val b = noisySkin(48, 48)
        for (y in 0 until 48) for (x in 24 until 48) b[x, y] = Colors.rgb(40, 30, 25) // hair-like edge
        val before = variance(PixelBuffer(24, 48, IntArray(24 * 48) { b[it % 24, it / 24] }))
        Retouch.smooth(b, full(48, 48), 1f, 3)
        val after = variance(PixelBuffer(16, 48, IntArray(16 * 48) { b[it % 16, it / 16] }))
        assertTrue("noise went down: $before -> $after", after < before * 0.5)
        assertTrue("dark side stays dark", Colors.luma(b[40, 20]) < 60f)
        assertTrue("bright side stays bright next to the edge", Colors.luma(b[22, 20]) > 120f)
    }

    @Test
    fun blemishFadesADarkSpot() {
        val b = PixelBuffer.filled(41, 41, skin)
        for (y in 19..21) for (x in 19..21) b[x, y] = Colors.rgb(120, 70, 60)
        val before = Colors.luma(b[20, 20])
        Retouch.blemish(b, full(41, 41), 1f, 1, 6)
        assertTrue(Colors.luma(b[20, 20]) > before + 25f)
        assertEquals(skin, b[3, 3])
    }

    @Test
    fun pullLowFrequencyMovesMeanTowardsTarget() {
        val dark = Colors.rgb(120, 90, 80)
        val b = PixelBuffer.filled(20, 20, dark)
        Retouch.pullLowFrequency(b, full(20, 20), 1f, 3, skin, strength = 1f)
        assertEquals(skin, b[10, 10])
        val half = PixelBuffer.filled(20, 20, dark)
        Retouch.pullLowFrequency(half, full(20, 20), 0.5f, 3, skin, strength = 1f)
        assertEquals(160, Colors.r(half[10, 10]))
    }

    @Test
    fun softenLinesFillsADarkCrease() {
        val b = PixelBuffer.filled(30, 30, skin)
        for (x in 0 until 30) b[x, 15] = Colors.rgb(140, 100, 85)
        val before = Colors.luma(b[15, 15])
        Retouch.softenLines(b, full(30, 30), 1f, 3)
        assertTrue(Colors.luma(b[15, 15]) > before + 20f)
    }

    @Test
    fun toothWeightSeparatesTeethFromGumsAndShadow() {
        assertTrue(Retouch.toothWeight(Colors.rgb(225, 215, 190)) > 0.8f)
        assertEquals(0f, Retouch.toothWeight(Colors.rgb(190, 80, 90)), 0.01f)   // gum / tongue
        assertEquals(0f, Retouch.toothWeight(Colors.rgb(30, 20, 20)), 0.01f)     // mouth shadow
    }

    @Test
    fun whiteningReducesYellowAndBrightens() {
        val yellowTooth = Colors.rgb(215, 200, 150)
        val b = PixelBuffer.filled(8, 8, yellowTooth)
        Retouch.whitenTeeth(b, full(8, 8), 1f)
        val c = b[4, 4]
        assertTrue(Colors.luma(c) > Colors.luma(yellowTooth))
        assertTrue(Colors.r(c) - Colors.b(c) < Colors.r(yellowTooth) - Colors.b(yellowTooth))
    }

    @Test
    fun brightenEyesLiftsWhitesAndRemovesCast() {
        val redEye = Colors.rgb(200, 170, 165)
        val b = PixelBuffer.filled(8, 8, redEye)
        Retouch.brightenEyes(b, full(8, 8), 1f)
        val c = b[4, 4]
        assertTrue(Colors.luma(c) > Colors.luma(redEye))
        assertTrue(Colors.saturation(c) < Colors.saturation(redEye))
    }

    @Test
    fun applyColorModes() {
        val m = full(4, 4)
        val red = Colors.rgb(200, 0, 0)
        val n = PixelBuffer.filled(4, 4, skin); Retouch.applyColor(n, m, red, 1f, Retouch.Blend.NORMAL)
        assertEquals(red, n[1, 1])
        val mu = PixelBuffer.filled(4, 4, skin); Retouch.applyColor(mu, m, red, 1f, Retouch.Blend.MULTIPLY)
        assertEquals(Colors.multiply(skin, red), mu[1, 1])
        val t = PixelBuffer.filled(4, 4, Colors.rgb(100, 100, 100)); Retouch.applyColor(t, m, red, 1f, Retouch.Blend.TINT, 100f)
        assertEquals(red, t[1, 1])
        val halfMask = RoiMask(0, 0, 4, 4).also { it.a.fill(0.5f) }
        val h = PixelBuffer.filled(4, 4, Colors.rgb(0, 0, 0)); Retouch.applyColor(h, halfMask, red, 1f, Retouch.Blend.NORMAL)
        assertEquals(100, Colors.r(h[1, 1]))
    }

    @Test
    fun maskOffsetWritesToTheRightPixels() {
        val b = PixelBuffer.filled(20, 20, 0xFF000000.toInt())
        val m = RoiMask(10, 5, 4, 3).also { it.a.fill(1f) }
        Retouch.applyColor(b, m, 0xFFFFFFFF.toInt(), 1f, Retouch.Blend.NORMAL)
        assertEquals(12, b.px.count { it == 0xFFFFFFFF.toInt() })
        assertEquals(0xFFFFFFFF.toInt(), b[10, 5]); assertEquals(0xFFFFFFFF.toInt(), b[13, 7])
        assertEquals(0xFF000000.toInt(), b[14, 7]); assertEquals(0xFF000000.toInt(), b[9, 5])
    }

    @Test
    fun gateByChromaRemovesNonSkinPixels() {
        val b = PixelBuffer.filled(10, 10, skin)
        b[5, 5] = Colors.rgb(20, 20, 30) // hair strand
        val m = full(10, 10)
        Retouch.gateByChroma(b, m, skin)
        assertEquals(1f, m.at(2, 2), 0.01f)
        assertEquals(0f, m.at(5, 5), 0.05f)
    }

    @Test
    fun foundationEvensTowardsShadeButKeepsBrightnessOrder() {
        val b = PixelBuffer(20, 10, IntArray(200) { if (it % 20 < 10) Colors.rgb(210, 160, 140) else Colors.rgb(150, 110, 95) })
        Retouch.foundation(b, full(20, 10), Colors.rgb(221, 174, 136), 1f, 1)
        assertTrue(Colors.luma(b[2, 5]) > Colors.luma(b[17, 5]))
        assertTrue(abs(Colors.r(b[2, 5]) - 210) < 40)
    }

    @Test
    fun tintByMaskOnlyTouchesMaskedArea() {
        val b = PixelBuffer.filled(40, 20, Colors.rgb(60, 50, 45))
        // left half is hair
        val mask = LowResMask(4, 2, floatArrayOf(1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f))
        Retouch.tintByMask(b, mask, Colors.rgb(40, 60, 200), 1f)
        assertEquals(Colors.rgb(60, 50, 45), b[36, 10])
        val c = b[4, 10]
        assertTrue("turned blue", Colors.b(c) > Colors.r(c) + 20)
    }

    @Test
    fun compositeBackgroundKeepsSubject() {
        val b = PixelBuffer.filled(10, 10, skin)
        val bg = IntArray(100) { 0xFF0000FF.toInt() }
        Retouch.compositeBackground(b, bg, 1f) { u, _ -> if (u < 0.5f) 1f else 0f }
        assertEquals(skin, b[2, 5])
        assertEquals(0xFF0000FF.toInt(), b[8, 5])
        val half = PixelBuffer.filled(10, 10, Colors.rgb(0, 0, 0))
        Retouch.compositeBackground(half, IntArray(100) { 0xFFFFFFFF.toInt() }, 0.5f) { _, _ -> 0f }
        assertEquals(128f, Colors.r(half[5, 5]).toFloat(), 1f)
    }
}
