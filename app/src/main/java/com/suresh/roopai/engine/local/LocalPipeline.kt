package com.suresh.roopai.engine.local

import com.suresh.roopai.core.imaging.Blur
import com.suresh.roopai.core.imaging.ColorFilters
import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.Geometry
import com.suresh.roopai.core.imaging.IRect
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.core.imaging.Retouch
import com.suresh.roopai.core.imaging.RoiMask
import com.suresh.roopai.core.imaging.WarpField
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.Tool
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The offline renderer. Pure Kotlin: takes pixels + analysis + operations, returns pixels.
 *
 * Operations are stored in the order the user added them, but inside one run they are rendered
 * in a fixed pipeline order so the result is deterministic and independent of slider history:
 * skin -> makeup -> eyes/teeth -> hair -> accessories -> background -> reshape -> colour -> geometry.
 * Reshape comes after makeup and accessories so they move with the face.
 */
object LocalPipeline {

    /** Supplies a background photo as w*h ARGB pixels (centre-cropped), or null if missing. */
    fun interface BackgroundSource {
        fun load(name: String, width: Int, height: Int): IntArray?
    }

    fun render(
        input: PixelBuffer,
        analysis: FaceAnalysis,
        ops: List<EditOp>,
        faceIndex: Int = 0,
        backgrounds: BackgroundSource? = null,
        checkCancelled: () -> Unit = {},
    ): PixelBuffer {
        val adj = HashMap<Tool, EditOp.Adjust>()
        var geometry: EditOp.Geometry? = null
        for (op in ops) {
            when (op) {
                is EditOp.Adjust -> if (!op.isNeutral) adj[op.tool] = op
                is EditOp.Geometry -> geometry = op
                is EditOp.CloudEdit -> Unit
            }
        }
        var buf = input
        val face = analysis.face(faceIndex)?.scaled(buf.width, buf.height)

        if (face != null) {
            skin(buf, face, adj); checkCancelled()
            makeup(buf, face, adj); checkCancelled()
            eyesAndTeeth(buf, face, adj); checkCancelled()
        }
        adj[Tool.HAIR_TINT]?.let { op ->
            analysis.hair?.let { Retouch.tintByMask(buf, it, op.color ?: Tool.HAIR_TINT.palette[1], op.amount) }
        }
        checkCancelled()
        if (face != null) Accessories.draw(buf, face, adj)
        background(buf, analysis, adj, backgrounds); checkCancelled()
        if (face != null) reshape(buf, face, adj)
        checkCancelled()
        colour(buf, adj); checkCancelled()
        geometry?.let { buf = applyGeometry(buf, it) }
        return buf
    }

    // ---------------------------------------------------------------- skin

    /** Feathered skin mask: face oval minus eyes, brows and lips, gated by skin chroma. */
    fun skinMask(buf: PixelBuffer, f: FaceGeom): RoiMask? {
        val oval = f.poly(FaceMesh.FACE_OVAL)
        val m = RoiMask.around(oval, f.fw * 0.06f, buf.width, buf.height) ?: return null
        m.fillPolygon(oval)
        val holes = RoiMask(m.left, m.top, m.width, m.height)
        holes.fillPolygon(f.poly(FaceMesh.EYE_R)).fillPolygon(f.poly(FaceMesh.EYE_L))
            .fillPolygon(f.poly(FaceMesh.BROW_R)).fillPolygon(f.poly(FaceMesh.BROW_L))
            .fillPolygon(f.poly(FaceMesh.LIPS_OUTER))
        holes.feather(px(f, 0.012f))
        // widen the holes a little so lashes and lip edges stay crisp
        for (i in holes.a.indices) holes.a[i] = min(1f, holes.a[i] * 1.8f)
        m.feather(px(f, 0.03f))
        m.subtract(holes)
        val ref = cheekColor(buf, f)
        if (ref != null) Retouch.gateByChroma(buf, m, ref)
        return m
    }

    private fun cheekColor(buf: PixelBuffer, f: FaceGeom): Int? {
        val r = f.fw * 0.05f
        val pts = floatArrayOf(f.x(FaceMesh.CHEEK_R), f.y(FaceMesh.CHEEK_R), f.x(FaceMesh.CHEEK_L), f.y(FaceMesh.CHEEK_L))
        val m = RoiMask.around(pts, r, buf.width, buf.height) ?: return null
        m.fillEllipse(pts[0], pts[1], r, r).fillEllipse(pts[2], pts[3], r, r)
        return Retouch.meanColor(buf, m)
    }

    private fun underEyeMask(buf: PixelBuffer, f: FaceGeom): RoiMask? {
        val right = f.poly(FaceMesh.LID_LOW_R + FaceMesh.UNDER_EYE_R)
        val left = f.poly(FaceMesh.LID_LOW_L + FaceMesh.UNDER_EYE_L)
        val m = RoiMask.around(right + left, f.fw * 0.05f, buf.width, buf.height) ?: return null
        m.fillPolygon(right).fillPolygon(left)
        val eyes = RoiMask(m.left, m.top, m.width, m.height)
            .fillPolygon(f.poly(FaceMesh.EYE_R)).fillPolygon(f.poly(FaceMesh.EYE_L))
        eyes.feather(px(f, 0.006f))
        m.feather(px(f, 0.016f))
        for (i in eyes.a.indices) eyes.a[i] = min(1f, eyes.a[i] * 2f)
        m.subtract(eyes)
        return m
    }

    private fun wrinkleMask(buf: PixelBuffer, f: FaceGeom): RoiMask? {
        val m = RoiMask.around(f.poly(FaceMesh.FACE_OVAL), f.fw * 0.1f, buf.width, buf.height) ?: return null
        m.fillPolygon(f.poly(FaceMesh.FOREHEAD))
        val r = f.fw * 0.085f
        // nasolabial folds: between nose wing and mouth corner
        m.fillEllipse(f.midX(FaceMesh.ALAR_R, FaceMesh.MOUTH_R), f.midY(FaceMesh.ALAR_R, FaceMesh.MOUTH_R), r, r * 1.5f, f.roll - 0.35f, 0.5f)
        m.fillEllipse(f.midX(FaceMesh.ALAR_L, FaceMesh.MOUTH_L), f.midY(FaceMesh.ALAR_L, FaceMesh.MOUTH_L), r, r * 1.5f, f.roll + 0.35f, 0.5f)
        // crow's feet: just outside the outer eye corners
        val o = f.fw * 0.07f
        m.fillEllipse(f.x(FaceMesh.EYE_OUT_R) - f.ux * o, f.y(FaceMesh.EYE_OUT_R) - f.uy * o, r * 0.8f, r, f.roll, 0.5f)
        m.fillEllipse(f.x(FaceMesh.EYE_OUT_L) + f.ux * o, f.y(FaceMesh.EYE_OUT_L) + f.uy * o, r * 0.8f, r, f.roll, 0.5f)
        m.fillPolygon(f.poly(FaceMesh.LID_LOW_R + FaceMesh.UNDER_EYE_R)).fillPolygon(f.poly(FaceMesh.LID_LOW_L + FaceMesh.UNDER_EYE_L))
        val holes = RoiMask(m.left, m.top, m.width, m.height)
            .fillPolygon(f.poly(FaceMesh.EYE_R)).fillPolygon(f.poly(FaceMesh.EYE_L))
            .fillPolygon(f.poly(FaceMesh.BROW_R)).fillPolygon(f.poly(FaceMesh.BROW_L))
            .fillPolygon(f.poly(FaceMesh.LIPS_OUTER))
        holes.feather(px(f, 0.01f))
        for (i in holes.a.indices) holes.a[i] = min(1f, holes.a[i] * 2f)
        m.feather(px(f, 0.02f))
        m.subtract(holes)
        return m
    }

    private fun skin(buf: PixelBuffer, f: FaceGeom, adj: Map<Tool, EditOp.Adjust>) {
        val needsSkin = adj.keys.any {
            it == Tool.SKIN_SMOOTH || it == Tool.SKIN_BLEMISH || it == Tool.SKIN_TONE || it == Tool.MAKEUP_FOUNDATION
        }
        val skin = if (needsSkin) skinMask(buf, f) else null
        if (skin != null) {
            adj[Tool.SKIN_TONE]?.let { op ->
                Retouch.meanColor(buf, skin)?.let { mean -> Retouch.pullLowFrequency(buf, skin, op.amount, px(f, 0.09f), mean, 0.6f) }
            }
            adj[Tool.SKIN_BLEMISH]?.let { Retouch.blemish(buf, skin, it.amount, px(f, 0.008f), px(f, 0.035f)) }
        }
        adj[Tool.SKIN_WRINKLES]?.let { op -> wrinkleMask(buf, f)?.let { Retouch.softenLines(buf, it, op.amount, px(f, 0.014f)) } }
        adj[Tool.SKIN_DARK_CIRCLES]?.let { op ->
            val m = underEyeMask(buf, f)
            val cheek = cheekColor(buf, f)
            if (m != null && cheek != null) Retouch.pullLowFrequency(buf, m, op.amount, px(f, 0.03f), cheek, 0.75f)
        }
        if (skin != null) {
            adj[Tool.SKIN_SMOOTH]?.let { Retouch.smooth(buf, skin, it.amount, px(f, 0.022f + 0.02f * it.amount)) }
            adj[Tool.MAKEUP_FOUNDATION]?.let {
                Retouch.foundation(buf, skin, it.color ?: Tool.MAKEUP_FOUNDATION.palette[2], it.amount, px(f, 0.03f))
            }
        }
    }

    // -------------------------------------------------------------- makeup

    fun lipMask(buf: PixelBuffer, f: FaceGeom): RoiMask? {
        val outer = f.poly(FaceMesh.LIPS_OUTER)
        val m = RoiMask.around(outer, f.fw * 0.03f, buf.width, buf.height) ?: return null
        m.fillPolygon(outer)
        val inner = RoiMask(m.left, m.top, m.width, m.height).fillPolygon(f.poly(FaceMesh.LIPS_INNER))
        m.subtract(inner)
        m.feather(max(1, px(f, 0.005f)))
        return m
    }

    private fun makeup(buf: PixelBuffer, f: FaceGeom, adj: Map<Tool, EditOp.Adjust>) {
        adj[Tool.MAKEUP_BLUSH]?.let { op ->
            val rx = f.fw * 0.13f; val ry = f.fw * 0.09f
            val up = f.fw * 0.03f
            val inward = f.fw * 0.02f
            val pts = floatArrayOf(
                f.x(FaceMesh.CHEEK_R) - f.vx * up + f.ux * inward, f.y(FaceMesh.CHEEK_R) - f.vy * up + f.uy * inward,
                f.x(FaceMesh.CHEEK_L) - f.vx * up - f.ux * inward, f.y(FaceMesh.CHEEK_L) - f.vy * up - f.uy * inward,
            )
            RoiMask.around(pts, rx * 1.2f, buf.width, buf.height)?.let { m ->
                m.fillEllipse(pts[0], pts[1], rx, ry, f.roll - 0.3f, 1f)
                m.fillEllipse(pts[2], pts[3], rx, ry, f.roll + 0.3f, 1f)
                // keep blush on the face when the head is turned
                val oval = RoiMask(m.left, m.top, m.width, m.height).fillPolygon(f.poly(FaceMesh.FACE_OVAL)).feather(px(f, 0.02f))
                for (i in m.a.indices) m.a[i] *= oval.a[i]
                Retouch.applyColor(buf, m, op.color ?: Tool.MAKEUP_BLUSH.palette[0], 0.5f * op.amount, Retouch.Blend.NORMAL)
            }
        }
        adj[Tool.MAKEUP_EYESHADOW]?.let { op ->
            val polys = listOf(shadowPoly(f, FaceMesh.LID_UP_R, FaceMesh.BROW_R), shadowPoly(f, FaceMesh.LID_UP_L, FaceMesh.BROW_L))
            RoiMask.around(polys[0] + polys[1], f.fw * 0.04f, buf.width, buf.height)?.let { m ->
                m.fillPolygon(polys[0]).fillPolygon(polys[1])
                m.feather(px(f, 0.012f))
                val eyes = RoiMask(m.left, m.top, m.width, m.height).fillPolygon(f.poly(FaceMesh.EYE_R)).fillPolygon(f.poly(FaceMesh.EYE_L))
                m.subtract(eyes)
                val color = op.color ?: Tool.MAKEUP_EYESHADOW.palette[0]
                Retouch.applyColor(buf, m, color, 0.6f * op.amount, Retouch.Blend.MULTIPLY)
                Retouch.applyColor(buf, m, color, 0.45f * op.amount, Retouch.Blend.NORMAL)
            }
        }
        adj[Tool.MAKEUP_EYELINER]?.let { op ->
            val wing = f.fw * 0.035f
            val lines = listOf(linerLine(f, FaceMesh.LID_UP_R, -1f, wing), linerLine(f, FaceMesh.LID_UP_L, 1f, wing))
            RoiMask.around(lines[0] + lines[1], f.fw * 0.03f, buf.width, buf.height)?.let { m ->
                val hw = max(0.9f, f.fw * 0.009f)
                // thin at the inner corner (t = 1), thickest two thirds out, pointed wing at t = 0
                val taper = { t: Float -> if (t < 0.12f) 0.25f + 6f * t else 1f - 0.75f * ((t - 0.12f) / 0.88f) }
                for (line in lines) m.strokePolyline(line, hw, 1f, taper)
                Retouch.applyColor(buf, m, op.color ?: Tool.MAKEUP_EYELINER.palette[0], min(1f, op.amount) * 0.92f, Retouch.Blend.NORMAL)
            }
        }
        adj[Tool.MAKEUP_KAJAL]?.let { op ->
            val lines = listOf(f.poly(FaceMesh.LID_LOW_R), f.poly(FaceMesh.LID_LOW_L))
            RoiMask.around(lines[0] + lines[1], f.fw * 0.03f, buf.width, buf.height)?.let { m ->
                val hw = max(0.8f, f.fw * 0.008f)
                for (line in lines) m.strokePolyline(line, hw, 1f) { t -> 1f - 0.6f * t }
                m.feather(max(1, px(f, 0.003f)))
                Retouch.applyColor(buf, m, op.color ?: Tool.MAKEUP_KAJAL.palette[0], min(1f, op.amount) * 0.95f, Retouch.Blend.NORMAL)
            }
        }
        adj[Tool.MAKEUP_LIPSTICK]?.let { op ->
            lipMask(buf, f)?.let { m ->
                val pivot = max(70f, (Retouch.meanColor(buf, m)?.let { Colors.luma(it) } ?: 120f) * 1.15f)
                Retouch.applyColor(buf, m, op.color ?: Tool.MAKEUP_LIPSTICK.palette[0], 0.85f * op.amount, Retouch.Blend.TINT, pivot)
            }
        }
        adj[Tool.BROW_COLOR]?.let { op ->
            browMask(buf, f)?.let { m ->
                val pivot = max(45f, (Retouch.meanColor(buf, m)?.let { Colors.luma(it) } ?: 80f) * 1.1f)
                Retouch.applyColor(buf, m, op.color ?: Tool.BROW_COLOR.palette[1], 0.7f * op.amount, Retouch.Blend.TINT, pivot)
            }
        }
        adj[Tool.BROW_THICKNESS]?.let { op ->
            // thicker brows are also denser; thinner brows are only reshaped (see reshape)
            if (op.amount > 0f) browMask(buf, f)?.let { Retouch.darken(buf, it, 0.3f * op.amount) }
        }
    }

    private fun browMask(buf: PixelBuffer, f: FaceGeom): RoiMask? {
        val r = f.poly(FaceMesh.BROW_R); val l = f.poly(FaceMesh.BROW_L)
        val m = RoiMask.around(r + l, f.fw * 0.03f, buf.width, buf.height) ?: return null
        m.fillPolygon(r).fillPolygon(l)
        m.feather(max(1, px(f, 0.008f)))
        return m
    }

    /** Area between the upper lid and roughly half way to the brow. */
    private fun shadowPoly(f: FaceGeom, lid: IntArray, brow: IntArray): FloatArray {
        val n = lid.size
        // lower brow edge is the second half of the brow polygon, reversed to run outer -> inner
        val browLow = IntArray(5) { brow[9 - it] }
        val out = FloatArray(n * 4)
        for (i in 0 until n) { out[2 * i] = f.x(lid[i]); out[2 * i + 1] = f.y(lid[i]) }
        for (i in 0 until n) {
            val k = n - 1 - i
            val t = k / (n - 1f)
            val bi = (t * 4f)
            val b0 = bi.toInt().coerceIn(0, 3); val bt = bi - b0
            val bx = f.x(browLow[b0]) * (1 - bt) + f.x(browLow[b0 + 1]) * bt
            val by = f.y(browLow[b0]) * (1 - bt) + f.y(browLow[b0 + 1]) * bt
            // tallest in the middle, pinched at both corners
            val lift = 0.62f * kotlin.math.sin(Math.PI.toFloat() * (0.12f + 0.76f * t))
            out[2 * (n + i)] = f.x(lid[k]) + (bx - f.x(lid[k])) * lift
            out[2 * (n + i) + 1] = f.y(lid[k]) + (by - f.y(lid[k])) * lift
        }
        return out
    }

    /** Upper lash line with a small wing past the outer corner. [side] is -1 for the right eye. */
    private fun linerLine(f: FaceGeom, lid: IntArray, side: Float, wing: Float): FloatArray {
        val out = FloatArray((lid.size + 1) * 2)
        out[0] = f.x(lid[0]) + f.ux * side * wing - f.vx * wing * 0.55f
        out[1] = f.y(lid[0]) + f.uy * side * wing - f.vy * wing * 0.55f
        for (i in lid.indices) { out[2 * (i + 1)] = f.x(lid[i]); out[2 * (i + 1) + 1] = f.y(lid[i]) }
        return out
    }

    // -------------------------------------------------------- eyes / teeth

    private fun eyesAndTeeth(buf: PixelBuffer, f: FaceGeom, adj: Map<Tool, EditOp.Adjust>) {
        adj[Tool.EYE_BRIGHTEN]?.let { op ->
            val r = f.poly(FaceMesh.EYE_R); val l = f.poly(FaceMesh.EYE_L)
            RoiMask.around(r + l, f.fw * 0.02f, buf.width, buf.height)?.let { m ->
                m.fillPolygon(r).fillPolygon(l)
                m.feather(max(1, px(f, 0.004f)))
                Retouch.brightenEyes(buf, m, op.amount)
            }
        }
        adj[Tool.TEETH_WHITEN]?.let { op ->
            val inner = f.poly(FaceMesh.LIPS_INNER)
            RoiMask.around(inner, f.fw * 0.02f, buf.width, buf.height)?.let { m ->
                m.fillPolygon(inner)
                m.feather(max(1, px(f, 0.004f)))
                Retouch.whitenTeeth(buf, m, op.amount)
            }
        }
    }

    // ---------------------------------------------------------- background

    private fun background(buf: PixelBuffer, a: FaceAnalysis, adj: Map<Tool, EditOp.Adjust>, sources: BackgroundSource?) {
        val bg = a.background ?: return
        val subject = { u: Float, v: Float -> 1f - Colors.smoothstep(0.35f, 0.65f, bg.sample(u, v)) }
        adj[Tool.BG_BLUR]?.let { op ->
            val radius = max(1, (max(buf.width, buf.height) * 0.022f * op.amount).roundToInt())
            val blurred = Blur.boxRgb(buf.px, buf.width, buf.height, radius)
            Retouch.compositeBackground(buf, blurred, 1f, subject)
        }
        adj[Tool.BG_REPLACE]?.let { op ->
            val backdrop = Backdrops.build(op, buf.width, buf.height, sources)
            Retouch.compositeBackground(buf, backdrop, op.amount, subject)
        }
    }

    // ------------------------------------------------------------- reshape

    /** Builds the mesh-warp field for all reshape sliders; exposed for tests. */
    fun buildWarp(f: FaceGeom, adj: Map<Tool, EditOp.Adjust>, imgW: Int, imgH: Int): WarpField? {
        val tools = listOf(
            Tool.FACE_SLIM, Tool.FACE_JAW, Tool.FACE_CHIN, Tool.FACE_FOREHEAD, Tool.NOSE_WIDTH,
            Tool.EYE_SIZE, Tool.LIP_FULLNESS, Tool.BROW_THICKNESS, Tool.BROW_SHAPE,
        )
        if (tools.none { adj.containsKey(it) }) return null
        val oval = f.poly(FaceMesh.FACE_OVAL)
        val pad = f.fw * 0.55f
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until oval.size / 2) {
            l = min(l, oval[2 * i]); r = max(r, oval[2 * i]); t = min(t, oval[2 * i + 1]); b = max(b, oval[2 * i + 1])
        }
        val region = IRect((l - pad).toInt(), (t - pad).toInt(), (r + pad).toInt() + 1, (b + pad).toInt() + 1).clip(imgW, imgH)
        if (region.isEmpty) return null
        val field = WarpField(region, max(2, (f.fw / 48f).roundToInt()))
        val fw = f.fw

        fun amount(tool: Tool) = adj[tool]?.amount?.coerceIn(-1f, 1f) ?: 0f

        // Slim: pull both cheek/jaw contours towards the face midline (positive = slimmer).
        amount(Tool.FACE_SLIM).let { a ->
            if (a != 0f) {
                val d = a * 0.045f * fw
                for (i in intArrayOf(FaceMesh.SIDE_R, 93, FaceMesh.EAR_LOW_R, FaceMesh.JAW_R)) field.addTranslate(f.x(i), f.y(i), f.ux * d, f.uy * d, fw * 0.36f)
                for (i in intArrayOf(FaceMesh.SIDE_L, 323, FaceMesh.EAR_LOW_L, FaceMesh.JAW_L)) field.addTranslate(f.x(i), f.y(i), -f.ux * d, -f.uy * d, fw * 0.36f)
            }
        }
        // Jaw: narrow (positive) or widen the lower jaw line.
        amount(Tool.FACE_JAW).let { a ->
            if (a != 0f) {
                val d = a * 0.05f * fw
                for (i in intArrayOf(FaceMesh.JAW_R, 136, FaceMesh.JAW_LOW_R)) field.addTranslate(f.x(i), f.y(i), f.ux * d, f.uy * d, fw * 0.26f)
                for (i in intArrayOf(FaceMesh.JAW_L, 365, FaceMesh.JAW_LOW_L)) field.addTranslate(f.x(i), f.y(i), -f.ux * d, -f.uy * d, fw * 0.26f)
            }
        }
        // Chin: longer (positive) or shorter.
        amount(Tool.FACE_CHIN).let { a ->
            if (a != 0f) {
                val d = a * 0.075f * fw
                field.addTranslate(f.x(FaceMesh.CHIN), f.y(FaceMesh.CHIN), f.vx * d, f.vy * d, fw * 0.34f)
            }
        }
        // Forehead: taller (positive) or lower hairline.
        amount(Tool.FACE_FOREHEAD).let { a ->
            if (a != 0f) {
                val d = a * 0.07f * fw
                val cx = f.x(FaceMesh.FOREHEAD_TOP) - f.vx * fw * 0.06f
                val cy = f.y(FaceMesh.FOREHEAD_TOP) - f.vy * fw * 0.06f
                field.addTranslate(cx, cy, -f.vx * d, -f.vy * d, fw * 0.5f)
            }
        }
        // Nose: wider (positive) or narrower nostrils.
        amount(Tool.NOSE_WIDTH).let { a ->
            if (a != 0f) {
                val d = a * 0.03f * fw
                for (i in intArrayOf(FaceMesh.ALAR_R, FaceMesh.NOSTRIL_R)) field.addTranslate(f.x(i), f.y(i), -f.ux * d, -f.uy * d, fw * 0.11f)
                for (i in intArrayOf(FaceMesh.ALAR_L, FaceMesh.NOSTRIL_L)) field.addTranslate(f.x(i), f.y(i), f.ux * d, f.uy * d, fw * 0.11f)
            }
        }
        amount(Tool.EYE_SIZE).let { a ->
            if (a != 0f) {
                val rad = f.dist(FaceMesh.EYE_OUT_R, FaceMesh.EYE_IN_R) * 0.95f
                field.addScale(f.x(FaceMesh.IRIS_R), f.y(FaceMesh.IRIS_R), rad, a * 0.3f)
                field.addScale(f.x(FaceMesh.IRIS_L), f.y(FaceMesh.IRIS_L), rad, a * 0.3f)
            }
        }
        amount(Tool.LIP_FULLNESS).let { a ->
            if (a != 0f) {
                val cx = f.midX(13, 14); val cy = f.midY(13, 14)
                val rad = f.dist(FaceMesh.MOUTH_R, FaceMesh.MOUTH_L) * 0.62f
                field.addScaleAlong(cx, cy, rad, a * 0.38f, f.vx, f.vy)
                field.addScale(cx, cy, rad, a * 0.06f)
            }
        }
        amount(Tool.BROW_THICKNESS).let { a ->
            if (a != 0f) {
                for (brow in listOf(FaceMesh.BROW_R, FaceMesh.BROW_L)) {
                    val cx = f.midX(brow[2], brow[7]); val cy = f.midY(brow[2], brow[7])
                    field.addScaleAlong(cx, cy, f.dist(brow[0], brow[4]) * 0.6f, a * 0.5f, f.vx, f.vy)
                }
            }
        }
        // Arch: lift the brow peak, drop the tail slightly.
        amount(Tool.BROW_SHAPE).let { a ->
            if (a != 0f) {
                val d = a * 0.03f * fw
                for ((peak, tail) in listOf(105 to 46, 334 to 276)) {
                    field.addTranslate(f.x(peak), f.y(peak), -f.vx * d, -f.vy * d, fw * 0.11f)
                    field.addTranslate(f.x(tail), f.y(tail), f.vx * d * 0.4f, f.vy * d * 0.4f, fw * 0.08f)
                }
            }
        }
        return field
    }

    private fun reshape(buf: PixelBuffer, f: FaceGeom, adj: Map<Tool, EditOp.Adjust>) {
        buildWarp(f, adj, buf.width, buf.height)?.apply(buf)
    }

    // -------------------------------------------------------------- colour

    fun colourMatrix(adj: Map<Tool, EditOp.Adjust>): FloatArray {
        fun a(tool: Tool) = adj[tool]?.amount?.coerceIn(-1f, 1f) ?: 0f
        var m = ColorFilters.adjustments(a(Tool.ADJ_BRIGHTNESS), a(Tool.ADJ_CONTRAST), a(Tool.ADJ_SATURATION), a(Tool.ADJ_WARMTH))
        adj[Tool.FILTER]?.let { op ->
            ColorFilters.filter(op.variant ?: Tool.FILTER.variants.first())?.let { fm ->
                m = ColorFilters.concat(ColorFilters.strength(fm, op.amount.coerceIn(0f, 1f)), m)
            }
        }
        return m
    }

    private fun colour(buf: PixelBuffer, adj: Map<Tool, EditOp.Adjust>) = ColorFilters.apply(buf, colourMatrix(adj))

    // ------------------------------------------------------------ geometry

    fun applyGeometry(src: PixelBuffer, g: EditOp.Geometry): PixelBuffer {
        var b = Geometry.rotate90(src, g.quarterTurns)
        b = Geometry.flip(b, g.flipH, g.flipV)
        b = Geometry.straighten(b, g.straighten)
        if (g.hasCrop) b = Geometry.crop(b, Geometry.cropRect(b.width, b.height, g.cropL, g.cropT, g.cropR, g.cropB))
        return b
    }

    /** Output size of [applyGeometry] without doing the work. */
    fun geometrySize(w: Int, h: Int, g: EditOp.Geometry?): Pair<Int, Int> {
        if (g == null) return w to h
        var ow = w; var oh = h
        if (((g.quarterTurns % 4) + 4) % 2 == 1) { ow = h; oh = w }
        if (kotlin.math.abs(g.straighten) >= 0.05f) {
            val k = Geometry.straightenScale(ow, oh, g.straighten)
            ow = max(1, (ow * k).toInt()); oh = max(1, (oh * k).toInt())
        }
        if (g.hasCrop) {
            val r = Geometry.cropRect(ow, oh, g.cropL, g.cropT, g.cropR, g.cropB)
            ow = r.width; oh = r.height
        }
        return ow to oh
    }

    private fun px(f: FaceGeom, fraction: Float): Int = max(1, (f.fw * fraction).roundToInt())

    /** True when at least one op in [ops] would change pixels. */
    fun hasWork(ops: List<EditOp>): Boolean = ops.any {
        (it is EditOp.Adjust && !it.isNeutral) || (it is EditOp.Geometry && !it.isIdentity)
    }
}
