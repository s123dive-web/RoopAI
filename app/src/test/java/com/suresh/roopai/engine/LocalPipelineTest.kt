package com.suresh.roopai.engine

import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.LowResMask
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.local.Accessories
import com.suresh.roopai.engine.local.Backdrops
import com.suresh.roopai.engine.local.FaceAnalysis
import com.suresh.roopai.engine.local.FaceLandmarks
import com.suresh.roopai.engine.local.FaceMesh
import com.suresh.roopai.engine.local.LocalPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real offline pipeline on a synthetic "photo" (skin-coloured canvas) with real
 * MediaPipe landmarks of a sample portrait (src/test/resources/face_landmarks.txt).
 */
class LocalPipelineTest {
    private val w = 240
    private val h = 340
    private val skin = Colors.rgb(205, 160, 135)

    private val face: FaceLandmarks by lazy {
        val text = javaClass.classLoader!!.getResource("face_landmarks.txt")!!.readText()
        FaceLandmarks(text.trim().split(' ').map { it.toFloat() }.toFloatArray())
    }

    /** Left half of the frame is "hair", the outer ring is "background". */
    private val analysis by lazy {
        val hair = FloatArray(16 * 16) { if (it / 16 < 3) 1f else 0f }
        val bg = FloatArray(16 * 16) { val x = it % 16; val y = it / 16; if (x < 2 || x > 13 || y < 1) 1f else 0f }
        FaceAnalysis(listOf(face), LowResMask(16, 16, hair), LowResMask(16, 16, bg))
    }

    private fun canvas(): PixelBuffer {
        val rnd = java.util.Random(3)
        val buf = PixelBuffer(w, h, IntArray(w * h) { val n = rnd.nextInt(13) - 6; Colors.rgb(205 + n, 160 + n, 135 + n) })
        // a small blemish on the cheek so spot removal has something to find
        val g = face.scaled(w, h)
        val cx = g.x(FaceMesh.CHEEK_R).toInt(); val cy = g.y(FaceMesh.CHEEK_R).toInt()
        for (y in cy - 1..cy + 1) for (x in cx - 1..cx + 1) buf[x, y] = Colors.rgb(120, 70, 60)
        return buf
    }

    private fun render(vararg ops: EditOp, a: FaceAnalysis = analysis, src: PixelBuffer = canvas()) =
        LocalPipeline.render(src, a, ops.toList())

    private fun diffCount(a: PixelBuffer, b: PixelBuffer): Int {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        return a.px.indices.count { a.px[it] != b.px[it] }
    }

    @Test
    fun fixtureIsACompleteFace() {
        assertEquals(FaceMesh.POINTS * 2, face.norm.size)
        val b = face.bounds
        assertTrue(b[0] in 0f..1f && b[2] > b[0] && b[3] > b[1])
        val g = face.scaled(w, h)
        assertTrue(g.fw > 20f)
        assertEquals(1f, g.ux * g.ux + g.uy * g.uy, 1e-3f)
        assertTrue("v points to the chin", g.vy > 0.8f)
        assertTrue("sample portrait is roughly frontal", kotlin.math.abs(g.yaw) < 0.3f)
        assertTrue("right eye is on the image left", g.x(FaceMesh.IRIS_R) < g.x(FaceMesh.IRIS_L))
    }

    @Test
    fun noOpsReturnsIdenticalPixels() {
        val src = canvas(); val before = src.px.copyOf()
        val out = LocalPipeline.render(src, analysis, emptyList())
        assertTrue(out.px.contentEquals(before))
        assertFalse(LocalPipeline.hasWork(emptyList()))
        assertFalse(LocalPipeline.hasWork(listOf(EditOp.Adjust(Tool.SKIN_SMOOTH, 0f), EditOp.Geometry())))
        assertTrue(LocalPipeline.hasWork(listOf(EditOp.Adjust(Tool.SKIN_SMOOTH, 0.1f))))
    }

    @Test
    fun everyToolChangesPixelsAtItsDefaultAmount() {
        val base = canvas()
        for (tool in Tool.entries) {
            val variants = if (tool.variants.isEmpty()) listOf(null) else tool.variants
            for (v in variants) {
                val op = EditOp.Adjust(tool, tool.defaultAmount, tool.palette.firstOrNull(), v)
                val out = render(op)
                assertTrue("${tool.name}/$v should change the image", diffCount(base, out) > 0)
            }
        }
    }

    @Test
    fun bipolarToolsDifferBySign() {
        for (tool in Tool.entries.filter { it.bipolar }) {
            val pos = render(EditOp.Adjust(tool, 0.8f))
            val neg = render(EditOp.Adjust(tool, -0.8f))
            assertTrue(tool.name, diffCount(pos, neg) > 0)
        }
    }

    @Test
    fun faceToolsAreSkippedWithoutAFaceButGlobalToolsStillWork() {
        val noFace = FaceAnalysis(emptyList(), analysis.hair, analysis.background)
        val base = canvas()
        for (tool in Tool.entries) {
            val out = render(EditOp.Adjust(tool, tool.defaultAmount, tool.palette.firstOrNull(), tool.variants.firstOrNull()), a = noFace)
            val changed = diffCount(base, out) > 0
            assertEquals(tool.name, tool.needs != com.suresh.roopai.edit.Needs.FACE, changed)
        }
    }

    @Test
    fun nothingCrashesWithoutAnyAnalysis() {
        val base = canvas()
        val ops = Tool.entries.map { EditOp.Adjust(it, it.defaultAmount, it.palette.firstOrNull(), it.variants.firstOrNull()) }
        val out = LocalPipeline.render(canvas(), FaceAnalysis.EMPTY, ops.filter { it.tool.needs != com.suresh.roopai.edit.Needs.NOTHING })
        assertEquals(0, diffCount(base, out))
    }

    @Test
    fun faceEditsStayNearTheFace() {
        val base = canvas()
        val out = render(EditOp.Adjust(Tool.SKIN_SMOOTH, 1f), EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 1f, Tool.MAKEUP_LIPSTICK.palette[0]))
        val b = face.bounds
        for (y in 0 until h) for (x in 0 until w) {
            val inside = x > (b[0] - 0.1f) * w && x < (b[2] + 0.1f) * w && y > (b[1] - 0.1f) * h && y < (b[3] + 0.1f) * h
            if (!inside) assertEquals(base[x, y], out[x, y])
        }
    }

    @Test
    fun lipstickPaintsLipsInTheChosenColour() {
        val red = Colors.rgb(190, 20, 40)
        val out = render(EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 1f, red))
        val g = face.scaled(w, h)
        // centre of the lower lip
        val x = ((g.x(17) + g.x(14)) / 2).toInt(); val y = ((g.y(17) + g.y(14)) / 2).toInt()
        val c = out[x, y]
        assertTrue("lower lip is red: ${Integer.toHexString(c)}", Colors.r(c) > Colors.g(c) + 60)
        val fx = g.x(FaceMesh.FOREHEAD_MID).toInt(); val fy = g.y(FaceMesh.FOREHEAD_MID).toInt()
        assertTrue("forehead untouched", kotlin.math.abs(Colors.r(out[fx, fy]) - 205) < 10)
    }

    @Test
    fun lipMaskExcludesTheMouthOpening() {
        val g = face.scaled(w * 4, h * 4)
        val m = LocalPipeline.lipMask(PixelBuffer.filled(w * 4, h * 4, skin), g)!!
        val lowerLip = m.at(((g.x(17) + g.x(14)) / 2).toInt(), ((g.y(17) + g.y(14)) / 2).toInt())
        assertTrue(lowerLip > 0.8f)
        assertEquals(0f, m.at(g.x(FaceMesh.NOSE_TIP).toInt(), g.y(FaceMesh.NOSE_TIP).toInt()), 0.01f)
    }

    @Test
    fun skinMaskCoversCheeksButNotEyesOrLips() {
        val buf = PixelBuffer.filled(w * 2, h * 2, skin)
        val g = face.scaled(w * 2, h * 2)
        val m = LocalPipeline.skinMask(buf, g)!!
        assertTrue(m.at(g.x(FaceMesh.CHEEK_R).toInt(), g.y(FaceMesh.CHEEK_R).toInt()) > 0.9f)
        assertTrue(m.at(g.x(FaceMesh.IRIS_L).toInt(), g.y(FaceMesh.IRIS_L).toInt()) < 0.2f)
        assertTrue(m.at(((g.x(17) + g.x(14)) / 2).toInt(), ((g.y(17) + g.y(14)) / 2).toInt()) < 0.2f)
        assertEquals(0f, m.at(2, 2), 0f)
    }

    @Test
    fun warpIsNullWithoutReshapeToolsAndPinnedAtTheRegionBorder() {
        val g = face.scaled(w, h)
        assertNull(LocalPipeline.buildWarp(g, mapOf(Tool.SKIN_SMOOTH to EditOp.Adjust(Tool.SKIN_SMOOTH, 1f)), w, h))
        val field = LocalPipeline.buildWarp(g, mapOf(Tool.FACE_SLIM to EditOp.Adjust(Tool.FACE_SLIM, 1f)), w, h)
        assertNotNull(field)
        // slimming samples from further outside on the subject's right cheek (image left)
        val (dx, _) = field!!.displacementAt(g.x(FaceMesh.EAR_LOW_R), g.y(FaceMesh.EAR_LOW_R))
        assertTrue("dx=$dx", dx < -1f)
        val (dx2, _) = field.displacementAt(g.x(FaceMesh.EAR_LOW_L), g.y(FaceMesh.EAR_LOW_L))
        assertTrue("dx2=$dx2", dx2 > 1f)
        // the nose is not dragged along
        val (nx, ny) = field.displacementAt(g.x(FaceMesh.NOSE_TIP), g.y(FaceMesh.NOSE_TIP))
        assertTrue(kotlin.math.abs(nx) < 1f && kotlin.math.abs(ny) < 1f)
    }

    @Test
    fun slimmingIsSymmetricInSign() {
        val g = face.scaled(w, h)
        val slim = LocalPipeline.buildWarp(g, mapOf(Tool.FACE_SLIM to EditOp.Adjust(Tool.FACE_SLIM, 0.5f)), w, h)!!
        val wide = LocalPipeline.buildWarp(g, mapOf(Tool.FACE_SLIM to EditOp.Adjust(Tool.FACE_SLIM, -0.5f)), w, h)!!
        val a = slim.displacementAt(g.x(FaceMesh.JAW_R), g.y(FaceMesh.JAW_R))
        val b = wide.displacementAt(g.x(FaceMesh.JAW_R), g.y(FaceMesh.JAW_R))
        assertEquals(a.first, -b.first, 1e-3f)
    }

    @Test
    fun hairTintOnlyRecoloursHairArea() {
        val base = canvas()
        val out = render(EditOp.Adjust(Tool.HAIR_TINT, 1f, Colors.rgb(40, 60, 200)))
        assertTrue(Colors.b(out[w / 2, 5]) > Colors.r(out[w / 2, 5]))
        assertEquals(base[w / 2, h - 5], out[w / 2, h - 5])
    }

    @Test
    fun backgroundReplaceKeepsSubjectAndFillsOutside() {
        val blue = 0xFF2040C0.toInt()
        val out = render(EditOp.Adjust(Tool.BG_REPLACE, 1f, blue, "colour"))
        assertEquals(blue, out[2, h / 2])
        assertEquals(blue, out[w - 3, h / 2])
        assertTrue(Colors.r(out[w / 2, h / 2]) > 180)
    }

    @Test
    fun backgroundPhotoComesFromTheSourceAndFallsBackToColour() {
        val green = 0xFF00FF00.toInt()
        val op = EditOp.Adjust(Tool.BG_REPLACE, 1f, 0xFF112233.toInt(), EditStack.PHOTO_PREFIX + "bg.jpg")
        var asked: String? = null
        val out = LocalPipeline.render(canvas(), analysis, listOf(op), backgrounds = { name, bw, bh -> asked = name; IntArray(bw * bh) { green } })
        assertEquals("bg.jpg", asked)
        assertEquals(green, out[2, h / 2])
        val missing = LocalPipeline.render(canvas(), analysis, listOf(op), backgrounds = { _, _, _ -> null })
        assertEquals(0xFF112233.toInt(), missing[2, h / 2])
        assertEquals(0xFF112233.toInt(), LocalPipeline.render(canvas(), analysis, listOf(op))[2, h / 2])
    }

    @Test
    fun backdropGenerators() {
        val g = Backdrops.gradient(0xFF808080.toInt(), 4, 50)
        assertTrue(Colors.luma(g[0]) > Colors.luma(g[49 * 4]))
        val s = Backdrops.studio(0xFF808080.toInt(), 50, 50)
        assertTrue(Colors.luma(s[21 * 50 + 25]) > Colors.luma(s[0]))
        assertTrue(Backdrops.flat(0x00123456, 3, 3).all { it == 0xFF123456.toInt() })
    }

    @Test
    fun colourMatrixCombinesFilterAndAdjustments() {
        val out = render(EditOp.Adjust(Tool.FILTER, 1f, variant = "mono"), EditOp.Adjust(Tool.ADJ_BRIGHTNESS, 0.5f))
        val c = out[10, h - 10]
        assertEquals(Colors.r(c), Colors.g(c)); assertEquals(Colors.g(c), Colors.b(c))
        assertTrue(Colors.r(c) > Colors.luma(skin))
    }

    @Test
    fun geometryIsAppliedLastAndSizeIsPredicted() {
        val g = EditOp.Geometry(quarterTurns = 1, cropL = 0.25f, cropR = 0.75f)
        val out = render(g)
        assertEquals(h / 2, out.width); assertEquals(w, out.height)
        assertEquals(out.width to out.height, LocalPipeline.geometrySize(w, h, g))
        val s = EditOp.Geometry(straighten = 6f, flipH = true)
        val so = render(s)
        assertEquals(so.width to so.height, LocalPipeline.geometrySize(w, h, s))
        assertEquals(w to h, LocalPipeline.geometrySize(w, h, null))
    }

    @Test
    fun opOrderInTheListDoesNotChangeTheResult() {
        val a = EditOp.Adjust(Tool.SKIN_SMOOTH, 0.7f)
        val b = EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 0.8f, Tool.MAKEUP_LIPSTICK.palette[1])
        val c = EditOp.Adjust(Tool.FACE_SLIM, 0.5f)
        val d = EditOp.Adjust(Tool.FILTER, 0.6f, variant = "warm")
        assertEquals(0, diffCount(render(a, b, c, d), render(d, c, b, a)))
    }

    @Test
    fun resultIsResolutionIndependent() {
        // the same edit at 2x must land on the same facial feature
        val red = Colors.rgb(190, 20, 40)
        val big = LocalPipeline.render(PixelBuffer.filled(w * 2, h * 2, skin), analysis, listOf(EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 1f, red)))
        val g = face.scaled(w * 2, h * 2)
        val c = big[((g.x(17) + g.x(14)) / 2).toInt(), ((g.y(17) + g.y(14)) / 2).toInt()]
        assertTrue(Colors.r(c) > Colors.g(c) + 60)
    }

    @Test
    fun faceIndexFallsBackToFirstFace() {
        val out = LocalPipeline.render(canvas(), analysis, listOf(EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 1f, Colors.rgb(190, 20, 40))), faceIndex = 7)
        assertTrue(diffCount(canvas(), out) > 0)
    }

    @Test
    fun cancellationHookIsCalled() {
        var calls = 0
        LocalPipeline.render(canvas(), analysis, listOf(EditOp.Adjust(Tool.SKIN_SMOOTH, 0.5f))) { calls++ }
        assertTrue(calls >= 3)
        try {
            LocalPipeline.render(canvas(), analysis, listOf(EditOp.Adjust(Tool.SKIN_SMOOTH, 0.5f))) { throw java.util.concurrent.CancellationException("stop") }
            org.junit.Assert.fail("should propagate")
        } catch (_: java.util.concurrent.CancellationException) {
        }
    }

    @Test
    fun accessorySizeMapping() {
        assertEquals(0.6f, Accessories.sizeOf(0f), 1e-6f)
        assertEquals(1.6f, Accessories.sizeOf(1f), 1e-6f)
        assertEquals(1.6f, Accessories.sizeOf(5f), 1e-6f)
    }

    @Test
    fun bindiIsDrawnBetweenTheBrows() {
        val red = Colors.rgb(192, 18, 31)
        val out = render(EditOp.Adjust(Tool.ACC_BINDI, 1f, red, "round"))
        val g = face.scaled(w, h)
        val x = (g.x(FaceMesh.GLABELLA) + (g.x(FaceMesh.FOREHEAD_MID) - g.x(FaceMesh.GLABELLA)) * 0.22f).toInt()
        val y = (g.y(FaceMesh.GLABELLA) + (g.y(FaceMesh.FOREHEAD_MID) - g.y(FaceMesh.GLABELLA)) * 0.22f).toInt()
        val c = out[x, y]
        assertTrue(Colors.r(c) > Colors.g(c) + 50)
    }
}
