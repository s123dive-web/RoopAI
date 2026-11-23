package com.suresh.roopai.core

import com.suresh.roopai.core.imaging.Colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorsTest {
    @Test
    fun packAndUnpackRoundTrip() {
        val c = Colors.argb(200, 10, 20, 30)
        assertEquals(200, Colors.a(c)); assertEquals(10, Colors.r(c)); assertEquals(20, Colors.g(c)); assertEquals(30, Colors.b(c))
    }

    @Test
    fun clampAndRgbSaturate() {
        assertEquals(0, Colors.clamp8(-5)); assertEquals(255, Colors.clamp8(999)); assertEquals(128, Colors.clamp8(127.6f))
        assertEquals(0xFFFF0000.toInt(), Colors.rgb(300, -1, 0))
    }

    @Test
    fun lumaOfGreyIsItsValue() {
        assertEquals(100f, Colors.luma(Colors.rgb(100, 100, 100)), 0.01f)
        assertTrue(Colors.luma(Colors.rgb(0, 255, 0)) > Colors.luma(Colors.rgb(255, 0, 0)))
    }

    @Test
    fun mixEndpointsAndMidpoint() {
        val a = Colors.rgb(0, 0, 0); val b = Colors.rgb(200, 100, 50)
        assertEquals(a, Colors.mix(a, b, 0f))
        assertEquals(b, Colors.mix(a, b, 1f))
        assertEquals(b, Colors.mix(a, b, 5f))
        assertEquals(Colors.rgb(100, 50, 25), Colors.mix(a, b, 0.5f))
    }

    @Test
    fun mixKeepsDestinationAlpha() {
        val dst = Colors.argb(77, 0, 0, 0)
        assertEquals(77, Colors.a(Colors.mix(dst, 0xFFFFFFFF.toInt(), 0.5f)))
    }

    @Test
    fun smoothstepIsMonotonicAndClamped() {
        assertEquals(0f, Colors.smoothstep(10f, 20f, 5f), 0f)
        assertEquals(1f, Colors.smoothstep(10f, 20f, 25f), 0f)
        assertEquals(0.5f, Colors.smoothstep(10f, 20f, 15f), 1e-4f)
        var prev = 0f
        for (i in 0..20) { val v = Colors.smoothstep(0f, 1f, i / 20f); assertTrue(v >= prev); prev = v }
    }

    @Test
    fun tintAtPivotGivesTheColour() {
        val grey = Colors.rgb(140, 140, 140)
        val red = Colors.rgb(180, 20, 40)
        assertEquals(red, Colors.tintKeepLuma(grey, red, 140f))
    }

    @Test
    fun tintKeepsShadowsDarkAndHighlightsBright() {
        val red = Colors.rgb(180, 20, 40)
        val dark = Colors.tintKeepLuma(Colors.rgb(30, 30, 30), red, 140f)
        val bright = Colors.tintKeepLuma(Colors.rgb(250, 250, 250), red, 140f)
        assertTrue(Colors.luma(dark) < Colors.luma(red))
        assertTrue(Colors.luma(bright) > Colors.luma(red))
        assertTrue("highlights keep the hue order", Colors.r(bright) >= Colors.g(bright))
    }

    @Test
    fun saturateZeroIsGreyAndOneIsIdentity() {
        val c = Colors.rgb(200, 50, 10)
        assertEquals(c, Colors.saturate(c, 1f))
        val g = Colors.saturate(c, 0f)
        assertEquals(Colors.r(g), Colors.g(g)); assertEquals(Colors.g(g), Colors.b(g))
    }

    @Test
    fun hsvRoundTrip() {
        for (c in listOf(Colors.rgb(255, 0, 0), Colors.rgb(12, 200, 99), Colors.rgb(30, 30, 30), Colors.rgb(250, 240, 10))) {
            val hsv = Colors.rgbToHsv(c)
            val back = Colors.hsvToRgb(hsv[0], hsv[1], hsv[2])
            assertEquals(Colors.r(c).toFloat(), Colors.r(back).toFloat(), 1.5f)
            assertEquals(Colors.g(c).toFloat(), Colors.g(back).toFloat(), 1.5f)
            assertEquals(Colors.b(c).toFloat(), Colors.b(back).toFloat(), 1.5f)
        }
    }
}
