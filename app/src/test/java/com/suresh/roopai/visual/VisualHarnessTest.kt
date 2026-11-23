package com.suresh.roopai.visual

import com.suresh.roopai.core.imaging.LowResMask
import com.suresh.roopai.core.imaging.PixelBuffer
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.local.FaceAnalysis
import com.suresh.roopai.engine.local.FaceLandmarks
import com.suresh.roopai.engine.local.LocalPipeline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer tool, skipped in normal runs. Renders every offline tool on a real photo so the
 * result can be eyeballed without a device:
 *
 *   ./gradlew testDebugUnitTest --tests '*VisualHarnessTest*' \
 *       -Droopai.visual.image=/path/photo.jpg -Droopai.visual.json=/path/analysis.json \
 *       -Droopai.visual.out=/path/out
 *
 * analysis.json = {"faces":[[x0,y0,...]], "hair":[256*256 floats], "bg":[256*256 floats]}
 * (landmarks normalised 0..1), e.g. dumped from the MediaPipe Python package.
 */
class VisualHarnessTest {
    @Test
    fun renderAllTools() {
        val image = System.getProperty("roopai.visual.image")
        val json = System.getProperty("roopai.visual.json")
        val out = System.getProperty("roopai.visual.out")
        assumeTrue("visual harness not configured", image != null && json != null && out != null)
        val img = ImageIO.read(File(image!!))
        val w = img.width; val h = img.height
        val px = IntArray(w * h)
        img.getRGB(0, 0, w, h, px, 0, w)
        for (i in px.indices) px[i] = px[i] or (0xFF shl 24)
        val root = Json.parseToJsonElement(File(json!!).readText()).jsonObject
        fun floats(key: String) = root[key]!!.jsonArray.map { it.jsonPrimitiveFloat() }.toFloatArray()
        val faces = root["faces"]!!.jsonArray.map { f -> FaceLandmarks(f.jsonArray.map { it.jsonPrimitiveFloat() }.toFloatArray()) }
        val analysis = FaceAnalysis(faces, LowResMask(256, 256, floats("hair")), LowResMask(256, 256, floats("bg")))
        val dir = File(out!!).apply { mkdirs() }

        fun save(name: String, ops: List<EditOp>) {
            val t0 = System.nanoTime()
            val res = LocalPipeline.render(PixelBuffer(w, h, px.copyOf()), analysis, ops)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val bi = BufferedImage(res.width, res.height, BufferedImage.TYPE_INT_RGB)
            bi.setRGB(0, 0, res.width, res.height, res.px, 0, res.width)
            ImageIO.write(bi, "jpg", File(dir, "$name.jpg"))
            println("visual: $name ${res.width}x${res.height} in ${ms}ms")
        }

        val only = System.getProperty("roopai.visual.only")
        for (tool in Tool.entries) {
            if (only != null && !tool.name.contains(only)) continue
            val amounts = if (tool.bipolar) listOf(-1f, 1f) else listOf(1f)
            val variants = if (tool.variants.isEmpty()) listOf(null) else tool.variants
            for (a in amounts) for (v in variants) {
                val name = tool.name.lowercase() + (if (tool.bipolar) (if (a < 0) "_neg" else "_pos") else "") + (v?.let { "_" + it.replace(' ', '-') } ?: "")
                save(name, listOf(EditOp.Adjust(tool, a, tool.palette.firstOrNull(), v)))
            }
        }
        if (only == null) {
            save(
                "combo",
                listOf(
                    EditOp.Adjust(Tool.SKIN_SMOOTH, 0.6f), EditOp.Adjust(Tool.SKIN_TONE, 0.5f),
                    EditOp.Adjust(Tool.MAKEUP_LIPSTICK, 0.7f, Tool.MAKEUP_LIPSTICK.palette[0]),
                    EditOp.Adjust(Tool.MAKEUP_EYELINER, 0.8f, Tool.MAKEUP_EYELINER.palette[0]),
                    EditOp.Adjust(Tool.FACE_SLIM, 0.5f), EditOp.Adjust(Tool.EYE_SIZE, 0.4f),
                    EditOp.Adjust(Tool.ACC_BINDI, 0.5f, Tool.ACC_BINDI.palette[0], "round"),
                    EditOp.Adjust(Tool.BG_BLUR, 0.8f),
                    EditOp.Geometry(straighten = 4f, cropL = 0.05f, cropR = 0.95f),
                ),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveFloat(): Float =
        (this as kotlinx.serialization.json.JsonPrimitive).float
}
