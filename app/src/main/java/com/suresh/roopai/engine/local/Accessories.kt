package com.suresh.roopai.engine.local

import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.core.imaging.Retouch
import com.suresh.roopai.core.imaging.RoiMask
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.Tool
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Landmark-anchored stickers drawn procedurally (no bitmap assets), so they scale to any
 * resolution, follow head roll and take any colour. The slider controls size.
 */
object Accessories {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    fun draw(buf: PixelBuffer, f: FaceGeom, adj: Map<Tool, EditOp.Adjust>) {
        adj[Tool.ACC_CAP]?.let { cap(buf, f, it) }
        adj[Tool.ACC_BINDI]?.let { bindi(buf, f, it) }
        adj[Tool.ACC_EARRINGS]?.let { earrings(buf, f, it) }
        adj[Tool.ACC_NOSE_PIN]?.let { nosePin(buf, f, it) }
        adj[Tool.ACC_GLASSES]?.let { glasses(buf, f, it) }
    }

    /** Slider 0..1 -> size multiplier 0.6..1.6 (0.5 = natural size 1.1). */
    fun sizeOf(amount: Float) = 0.6f + amount.coerceIn(0f, 1f)

    private class Painter(val buf: PixelBuffer, val f: FaceGeom) {
        /** Face-local -> image: [a] along the eye line, [b] towards the chin. */
        fun px(ox: Float, oy: Float, a: Float, b: Float) = ox + f.ux * a + f.vx * b
        fun py(ox: Float, oy: Float, a: Float, b: Float) = oy + f.uy * a + f.vy * b

        fun mask(pts: FloatArray, pad: Float) = RoiMask.around(pts, pad, buf.width, buf.height)

        fun paint(m: RoiMask, color: Int, opacity: Float = 1f) =
            Retouch.applyColor(buf, m, color, opacity, Retouch.Blend.NORMAL)

        fun disc(cx: Float, cy: Float, r: Float, color: Int, opacity: Float = 1f, soft: Float = 0.25f) {
            val m = mask(floatArrayOf(cx, cy), r + 2f) ?: return
            m.fillEllipse(cx, cy, r, r, 0f, soft.coerceAtMost(if (r < 2f) 0.8f else soft))
            paint(m, color, opacity)
        }

        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, opacity: Float = 1f, soft: Float = 0.2f) {
            val m = mask(floatArrayOf(cx, cy), max(rx, ry) + 2f) ?: return
            m.fillEllipse(cx, cy, rx, ry, f.roll, soft)
            paint(m, color, opacity)
        }

        fun polygon(pts: FloatArray, color: Int, opacity: Float = 1f, feather: Int = 1) {
            val m = mask(pts, feather + 2f) ?: return
            m.fillPolygon(pts)
            if (feather > 0) m.feather(feather)
            paint(m, color, opacity)
        }

        fun stroke(pts: FloatArray, halfWidth: Float, color: Int, opacity: Float = 1f) {
            val m = mask(pts, halfWidth + 3f) ?: return
            m.strokePolyline(pts, halfWidth)
            paint(m, color, opacity)
        }

        /** Sphere-like bead: body, dark rim side, highlight. */
        fun bead(cx: Float, cy: Float, r: Float, color: Int) {
            disc(cx, cy, r, Colors.mix(color, BLACK, 0.35f))
            disc(cx - r * 0.12f, cy - r * 0.12f, r * 0.82f, color)
            disc(cx - r * 0.32f, cy - r * 0.34f, r * 0.3f, WHITE, 0.75f, 0.6f)
        }

        fun ring(cx: Float, cy: Float, rx: Float, ry: Float, halfWidth: Float, color: Int, from: Float = 0f, to: Float = (2 * PI).toFloat()) {
            val n = 28
            val pts = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                val t = from + (to - from) * i / n
                pts[2 * i] = px(cx, cy, cos(t) * rx, sin(t) * ry)
                pts[2 * i + 1] = py(cx, cy, cos(t) * rx, sin(t) * ry)
            }
            stroke(pts, halfWidth, color)
        }
    }

    // --------------------------------------------------------------- bindi

    private fun bindi(buf: PixelBuffer, f: FaceGeom, op: EditOp.Adjust) {
        val p = Painter(buf, f)
        val color = op.color ?: Tool.ACC_BINDI.palette[0]
        val s = sizeOf(op.amount)
        // between the brows, a little towards the forehead
        val cx = f.x(FaceMesh.GLABELLA) + (f.x(FaceMesh.FOREHEAD_MID) - f.x(FaceMesh.GLABELLA)) * 0.22f
        val cy = f.y(FaceMesh.GLABELLA) + (f.y(FaceMesh.FOREHEAD_MID) - f.y(FaceMesh.GLABELLA)) * 0.22f
        val r = f.fw * 0.021f * s
        when (op.variant ?: "round") {
            "teardrop" -> {
                val pts = FloatArray(40)
                for (i in 0 until 20) {
                    val t = (2 * PI * i / 20).toFloat()
                    // teardrop: circle whose top is pulled into a point
                    val a = sin(t) * r * (0.55f + 0.45f * (1f - cos(t)) / 2f)
                    val b = -cos(t) * r * (if (cos(t) > 0) 1.9f else 1f)
                    pts[2 * i] = p.px(cx, cy, a, b); pts[2 * i + 1] = p.py(cx, cy, a, b)
                }
                p.polygon(pts, Colors.mix(color, BLACK, 0.25f), 1f, 1)
                p.disc(cx, cy, r * 0.62f, color)
            }
            "long" -> {
                val ex = p.px(cx, cy, 0f, -r * 0.9f); val ey = p.py(cx, cy, 0f, -r * 0.9f)
                p.ellipse(ex, ey, r * 0.62f, r * 2.3f, Colors.mix(color, BLACK, 0.3f))
                p.ellipse(ex, ey, r * 0.48f, r * 2.1f, color)
            }
            else -> {
                p.disc(cx, cy, r, Colors.mix(color, BLACK, 0.3f))
                p.disc(cx, cy, r * 0.86f, color)
            }
        }
        p.disc(cx - r * 0.25f, cy - r * 0.28f, r * 0.24f, WHITE, 0.4f, 0.7f)
    }

    // ------------------------------------------------------------ earrings

    private fun earrings(buf: PixelBuffer, f: FaceGeom, op: EditOp.Adjust) {
        val p = Painter(buf, f)
        val color = op.color ?: Tool.ACC_EARRINGS.palette[0]
        val s = sizeOf(op.amount)
        val yaw = f.yaw
        // side: -1 = subject's right ear (image left)
        for (side in intArrayOf(-1, 1)) {
            // hide the ear that is turned away from the camera
            if (side == -1 && yaw < -0.3f) continue
            if (side == 1 && yaw > 0.3f) continue
            val top = if (side == -1) FaceMesh.SIDE_R else FaceMesh.SIDE_L
            val low = if (side == -1) FaceMesh.EAR_LOW_R else FaceMesh.EAR_LOW_L
            val out = f.fw * 0.012f * side
            val lx = f.x(top) + (f.x(low) - f.x(top)) * 0.78f + f.ux * out
            val ly = f.y(top) + (f.y(low) - f.y(top)) * 0.78f + f.uy * out
            val r = f.fw * 0.02f * s
            when (op.variant ?: "stud") {
                "drop" -> {
                    p.bead(lx, ly, r * 0.7f, color)
                    val dx = p.px(lx, ly, 0f, r * 2.6f); val dy = p.py(lx, ly, 0f, r * 2.6f)
                    p.stroke(floatArrayOf(lx, ly, dx, dy), max(0.6f, r * 0.12f), Colors.mix(color, BLACK, 0.2f))
                    val pts = FloatArray(36)
                    for (i in 0 until 18) {
                        val t = (2 * PI * i / 18).toFloat()
                        val a = sin(t) * r * 1.05f * (0.5f + 0.5f * (1f - cos(t)) / 2f + 0.25f)
                        val b = -cos(t) * r * (if (cos(t) > 0) 2.1f else 1.25f)
                        pts[2 * i] = p.px(dx, dy, a, b + r * 1.2f); pts[2 * i + 1] = p.py(dx, dy, a, b + r * 1.2f)
                    }
                    p.polygon(pts, Colors.mix(color, BLACK, 0.3f))
                    p.bead(p.px(dx, dy, 0f, r * 1.3f), p.py(dx, dy, 0f, r * 1.3f), r * 0.85f, color)
                }
                "hoop" -> {
                    val cy0 = r * 2.3f
                    p.ring(p.px(lx, ly, 0f, cy0), p.py(lx, ly, 0f, cy0), r * 1.6f, r * 2.3f, max(0.8f, r * 0.2f), Colors.mix(color, BLACK, 0.25f))
                    p.ring(p.px(lx, ly, -r * 0.1f, cy0 - r * 0.1f), p.py(lx, ly, -r * 0.1f, cy0 - r * 0.1f), r * 1.6f, r * 2.3f, max(0.5f, r * 0.1f), Colors.mix(color, WHITE, 0.35f))
                }
                "jhumka" -> {
                    p.bead(lx, ly, r * 0.75f, color)
                    // bell: half dome hanging below the stud
                    val bx = p.px(lx, ly, 0f, r * 1.2f); val by = p.py(lx, ly, 0f, r * 1.2f)
                    val n = 14
                    val pts = FloatArray((n + 1) * 2)
                    for (i in 0..n) {
                        val t = (PI + PI * i / n).toFloat()
                        val a = cos(t) * r * 1.7f
                        val b = sin(t) * r * 1.9f + r * 2.1f
                        pts[2 * i] = p.px(bx, by, a, b); pts[2 * i + 1] = p.py(bx, by, a, b)
                    }
                    p.polygon(pts, Colors.mix(color, BLACK, 0.2f))
                    p.ellipse(p.px(bx, by, -r * 0.4f, r * 1.1f), p.py(bx, by, -r * 0.4f, r * 1.1f), r * 0.5f, r * 0.8f, Colors.mix(color, WHITE, 0.5f), 0.6f, 0.8f)
                    for (k in -2..2) {
                        p.bead(p.px(bx, by, k * r * 0.75f, r * 2.45f), p.py(bx, by, k * r * 0.75f, r * 2.45f), r * 0.3f, color)
                    }
                }
                else -> p.bead(lx, ly, r, color)
            }
        }
    }

    // ------------------------------------------------------------ nose pin

    private fun nosePin(buf: PixelBuffer, f: FaceGeom, op: EditOp.Adjust) {
        val p = Painter(buf, f)
        val color = op.color ?: Tool.ACC_NOSE_PIN.palette[0]
        val s = sizeOf(op.amount)
        val variant = op.variant ?: "stud left"
        val left = variant.endsWith("left")
        val wing = if (left) FaceMesh.NOSE_WING_L else FaceMesh.NOSE_WING_R
        val nostril = if (left) FaceMesh.NOSTRIL_L else FaceMesh.NOSTRIL_R
        val r = f.fw * 0.0095f * s
        if (variant.startsWith("ring")) {
            val cx = f.x(nostril); val cy = f.y(nostril)
            val rr = f.fw * 0.028f * s
            // open hoop hugging the nostril edge
            p.ring(cx, cy, rr, rr * 1.1f, max(0.6f, rr * 0.12f), Colors.mix(color, BLACK, 0.2f), 0.2f, (PI * 1.25).toFloat())
            p.bead(p.px(cx, cy, cos(0.2f) * rr, sin(0.2f) * rr * 1.1f), p.py(cx, cy, cos(0.2f) * rr, sin(0.2f) * rr * 1.1f), max(1f, rr * 0.22f), color)
        } else {
            // on the nose wing, nudged towards the tip so it never lands on the cheek
            val cx = f.x(wing) + (f.x(FaceMesh.NOSE_TIP) - f.x(wing)) * 0.14f
            val cy = f.y(wing) + (f.y(FaceMesh.NOSE_TIP) - f.y(wing)) * 0.14f
            p.bead(cx, cy, max(1.2f, r), color)
            // sparkle
            p.stroke(floatArrayOf(cx - r * 1.6f, cy, cx + r * 1.6f, cy), max(0.4f, r * 0.12f), WHITE, 0.55f)
            p.stroke(floatArrayOf(cx, cy - r * 1.6f, cx, cy + r * 1.6f), max(0.4f, r * 0.12f), WHITE, 0.55f)
        }
    }

    // ------------------------------------------------------------- glasses

    private fun glasses(buf: PixelBuffer, f: FaceGeom, op: EditOp.Adjust) {
        val p = Painter(buf, f)
        val color = op.color ?: Tool.ACC_GLASSES.palette[0]
        val s = 0.85f + 0.3f * op.amount.coerceIn(0f, 1f)
        val variant = op.variant ?: "round"
        val rx0 = f.x(FaceMesh.IRIS_R); val ry0 = f.y(FaceMesh.IRIS_R)
        val lx0 = f.x(FaceMesh.IRIS_L); val ly0 = f.y(FaceMesh.IRIS_L)
        val ipd = max(4f, kotlin.math.hypot(lx0 - rx0, ly0 - ry0))
        val lensRx = ipd * 0.39f * s
        val lensRy = ipd * (if (variant == "square") 0.27f else 0.31f) * s
        val frame = max(0.8f, ipd * 0.022f)
        val drop = ipd * 0.04f // lenses sit slightly below the pupil line

        fun lensOutline(cx: Float, cy: Float, side: Int): FloatArray {
            val n = 32
            val pts = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                val t = (2 * PI * i / n).toFloat()
                var a = cos(t); var b = sin(t)
                when (variant) {
                    "square" -> {
                        // superellipse: rounded rectangle
                        val e = 0.42f
                        a = Math.signum(a) * Math.pow(abs(a).toDouble(), e.toDouble()).toFloat()
                        b = Math.signum(b) * Math.pow(abs(b).toDouble(), e.toDouble()).toFloat()
                    }
                    "aviator", "sunglasses" -> {
                        // teardrop: wider and lower on the outer-bottom side
                        // flat brow line, deep rounded bottom leaning outwards
                        if (b > 0) b *= 1.3f else b *= 0.72f
                        a += 0.12f * side * max(0f, b)
                    }
                }
                pts[2 * i] = p.px(cx, cy, a * lensRx, b * lensRy + drop)
                pts[2 * i + 1] = p.py(cx, cy, a * lensRx, b * lensRy + drop)
            }
            return pts
        }

        val right = lensOutline(rx0, ry0, -1)
        val left = lensOutline(lx0, ly0, 1)
        val tinted = variant == "sunglasses"
        for (lens in listOf(right, left)) {
            if (tinted) {
                p.polygon(lens, 0xFF14161C.toInt(), 0.88f, 1)
            } else {
                p.polygon(lens, WHITE, 0.07f, 1) // faint lens glare
            }
        }
        if (tinted) {
            // reflection streak
            for ((cx, cy) in listOf(rx0 to ry0, lx0 to ly0)) {
                p.stroke(
                    floatArrayOf(
                        p.px(cx, cy, -lensRx * 0.45f, -lensRy * 0.2f), p.py(cx, cy, -lensRx * 0.45f, -lensRy * 0.2f),
                        p.px(cx, cy, -lensRx * 0.05f, -lensRy * 0.55f), p.py(cx, cy, -lensRx * 0.05f, -lensRy * 0.55f),
                    ),
                    frame * 1.2f, WHITE, 0.22f,
                )
            }
        }
        p.stroke(right, frame, color)
        p.stroke(left, frame, color)
        // bridge
        val by = drop - lensRy * 0.25f
        p.stroke(
            floatArrayOf(
                p.px(rx0, ry0, lensRx * 0.96f, by), p.py(rx0, ry0, lensRx * 0.96f, by),
                p.px((rx0 + lx0) / 2, (ry0 + ly0) / 2, 0f, by - lensRy * 0.12f), p.py((rx0 + lx0) / 2, (ry0 + ly0) / 2, 0f, by - lensRy * 0.12f),
                p.px(lx0, ly0, -lensRx * 0.96f, by), p.py(lx0, ly0, -lensRx * 0.96f, by),
            ),
            frame, color,
        )
        // temples towards the ears
        p.stroke(floatArrayOf(p.px(rx0, ry0, -lensRx, by), p.py(rx0, ry0, -lensRx, by), f.x(FaceMesh.TEMPLE_R), f.y(FaceMesh.TEMPLE_R)), frame, color)
        p.stroke(floatArrayOf(p.px(lx0, ly0, lensRx, by), p.py(lx0, ly0, lensRx, by), f.x(FaceMesh.TEMPLE_L), f.y(FaceMesh.TEMPLE_L)), frame, color)
    }

    // ----------------------------------------------------------------- cap

    /** Dome profile for t in 0..PI: 0 at both ends, 1 at the top, slightly flattened. */
    private fun arch(t: Float): Float = Math.pow(max(0f, sin(t)).toDouble(), 0.75).toFloat()

    private fun cap(buf: PixelBuffer, f: FaceGeom, op: EditOp.Adjust) {
        val p = Painter(buf, f)
        val color = op.color ?: Tool.ACC_CAP.palette[0]
        val s = 0.9f + 0.3f * op.amount.coerceIn(0f, 1f)
        val variant = op.variant ?: "baseball"
        // origin: middle of the forehead top; the cap band sits just below it
        val ox = f.x(FaceMesh.FOREHEAD_TOP); val oy = f.y(FaceMesh.FOREHEAD_TOP)
        val half = f.fw * 0.62f * s
        val band = f.fw * 0.17f          // how far below the hairline the cap comes
        val rise = f.fw * (if (variant == "beanie") 0.72f else 0.6f) * s
        val n = 28
        val dome = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val t = (PI * i / n).toFloat()
            val a = -cos(t) * half
            // slightly flattened top so it reads as fabric, not a helmet
            val b = band - arch(t) * (rise + band)
            dome[2 * i] = p.px(ox, oy, a, b); dome[2 * i + 1] = p.py(ox, oy, a, b)
        }
        val feather = max(1, (f.fw * 0.004f).toInt())
        p.polygon(dome, Colors.mix(color, BLACK, 0.28f), 1f, feather)
        // lit upper-left area
        val hi = FloatArray(dome.size)
        for (i in 0..n) {
            val t = (PI * i / n).toFloat()
            val a = -cos(t) * half * 0.86f - half * 0.05f
            val b = band * 0.5f - arch(t) * (rise * 0.93f + band * 0.5f)
            hi[2 * i] = p.px(ox, oy, a, b); hi[2 * i + 1] = p.py(ox, oy, a, b)
        }
        p.polygon(hi, color, 1f, max(1, (f.fw * 0.02f).toInt()))

        if (variant == "beanie") {
            // ribbed cuff
            val cuff = floatArrayOf(
                p.px(ox, oy, -half, band), p.py(ox, oy, -half, band),
                p.px(ox, oy, -half * 0.98f, band - f.fw * 0.16f), p.py(ox, oy, -half * 0.98f, band - f.fw * 0.16f),
                p.px(ox, oy, half * 0.98f, band - f.fw * 0.16f), p.py(ox, oy, half * 0.98f, band - f.fw * 0.16f),
                p.px(ox, oy, half, band), p.py(ox, oy, half, band),
            )
            p.polygon(cuff, Colors.mix(color, BLACK, 0.18f), 1f, feather)
            val ribs = 14
            for (k in 1 until ribs) {
                val a = -half + 2 * half * k / ribs
                p.stroke(
                    floatArrayOf(p.px(ox, oy, a, band - f.fw * 0.01f), p.py(ox, oy, a, band - f.fw * 0.01f), p.px(ox, oy, a, band - f.fw * 0.15f), p.py(ox, oy, a, band - f.fw * 0.15f)),
                    max(0.5f, f.fw * 0.004f), BLACK, 0.22f,
                )
            }
            // pom-pom
            p.bead(p.px(ox, oy, 0f, -rise - f.fw * 0.03f), p.py(ox, oy, 0f, -rise - f.fw * 0.03f), f.fw * 0.09f * s, Colors.mix(color, WHITE, 0.25f))
        } else {
            // brim: flattened ellipse in front of the band
            val brim = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                val t = (2 * PI * i / n).toFloat()
                val a = cos(t) * half * 0.98f
                val b = band + sin(t) * f.fw * 0.085f + f.fw * 0.05f
                brim[2 * i] = p.px(ox, oy, a, b); brim[2 * i + 1] = p.py(ox, oy, a, b)
            }
            p.polygon(brim, Colors.mix(color, BLACK, 0.42f), 1f, feather)
            val top = FloatArray((n + 1) * 2)
            for (i in 0..n) {
                val t = (2 * PI * i / n).toFloat()
                val a = cos(t) * half * 0.94f
                val b = band + sin(t) * f.fw * 0.06f + f.fw * 0.035f
                top[2 * i] = p.px(ox, oy, a, b); top[2 * i + 1] = p.py(ox, oy, a, b)
            }
            p.polygon(top, Colors.mix(color, BLACK, 0.15f), 1f, feather)
            // crown seam and button
            p.stroke(
                floatArrayOf(p.px(ox, oy, 0f, band), p.py(ox, oy, 0f, band), p.px(ox, oy, 0f, -rise), p.py(ox, oy, 0f, -rise)),
                max(0.5f, f.fw * 0.004f), BLACK, 0.25f,
            )
            p.bead(p.px(ox, oy, 0f, -rise), p.py(ox, oy, 0f, -rise), f.fw * 0.022f, color)
        }
    }
}
