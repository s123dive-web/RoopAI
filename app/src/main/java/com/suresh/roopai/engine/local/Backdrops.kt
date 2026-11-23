package com.suresh.roopai.engine.local

import com.suresh.roopai.core.imaging.Colors
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Tool
import kotlin.math.sqrt

/** Generates replacement backgrounds: flat colour, gradient, studio vignette or a user photo. */
object Backdrops {
    fun build(op: EditOp.Adjust, w: Int, h: Int, sources: LocalPipeline.BackgroundSource?): IntArray {
        val color = op.color ?: Tool.BG_REPLACE.palette[0]
        val variant = op.variant ?: "colour"
        if (variant.startsWith(EditStack.PHOTO_PREFIX)) {
            sources?.load(variant.removePrefix(EditStack.PHOTO_PREFIX), w, h)?.let { if (it.size == w * h) return it }
            return flat(color, w, h)
        }
        return when (variant) {
            "gradient" -> gradient(color, w, h)
            "studio" -> studio(color, w, h)
            else -> flat(color, w, h)
        }
    }

    fun flat(color: Int, w: Int, h: Int) = IntArray(w * h) { color or (0xFF shl 24) }

    /** Vertical gradient from a lighter tint at the top to a deeper shade at the bottom. */
    fun gradient(color: Int, w: Int, h: Int): IntArray {
        val top = Colors.mix(color, 0xFFFFFFFF.toInt(), 0.35f)
        val bottom = Colors.mix(color, 0xFF000000.toInt(), 0.35f)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val c = Colors.mix(top, bottom, y / (h - 1f).coerceAtLeast(1f)) or (0xFF shl 24)
            java.util.Arrays.fill(out, y * w, (y + 1) * w, c)
        }
        return out
    }

    /** Photo-studio look: bright centre falling off to darker corners. */
    fun studio(color: Int, w: Int, h: Int): IntArray {
        val center = Colors.mix(color, 0xFFFFFFFF.toInt(), 0.25f)
        val edge = Colors.mix(color, 0xFF000000.toInt(), 0.45f)
        val out = IntArray(w * h)
        val cx = w / 2f; val cy = h * 0.42f
        val norm = sqrt(cx * cx + (h - cy) * (h - cy))
        for (y in 0 until h) for (x in 0 until w) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / norm
            out[y * w + x] = Colors.mix(center, edge, Colors.smoothstep(0.1f, 1f, d)) or (0xFF shl 24)
        }
        return out
    }
}
