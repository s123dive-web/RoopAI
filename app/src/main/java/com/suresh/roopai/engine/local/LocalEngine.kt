package com.suresh.roopai.engine.local

import android.graphics.Bitmap
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.FaceEditEngine
import com.suresh.roopai.edit.Needs
import com.suresh.roopai.edit.RenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Fully offline engine: MediaPipe analysis + the pure-Kotlin [LocalPipeline]. */
@Singleton
class LocalEngine @Inject constructor(private val analyzer: FaceAnalysisSource) : FaceEditEngine {
    override val id = "local"
    override val isOffline = true

    override fun supports(op: EditOp) = op is EditOp.Adjust || op is EditOp.Geometry

    /** Faces of the image, for the face picker and the "no face" state. */
    suspend fun analyze(source: Bitmap, analysisKey: String, needSegmentation: Boolean = false): FaceAnalysis =
        analyzer.analyze(analysisKey, source, needSegmentation)

    override suspend fun apply(source: Bitmap, ops: List<EditOp>, request: RenderRequest): Bitmap {
        val job = coroutineContext
        return withContext(Dispatchers.Default) {
            try {
                val active = ops.filter { (it is EditOp.Adjust && !it.isNeutral) || (it is EditOp.Geometry && !it.isIdentity) }
                val needs = active.filterIsInstance<EditOp.Adjust>().map { it.tool.needs }.toSet()
                val analysis = if (Needs.FACE in needs || Needs.SEGMENTATION in needs) {
                    analyzer.analyze(request.analysisKey, source, Needs.SEGMENTATION in needs)
                } else {
                    FaceAnalysis.EMPTY
                }
                job.ensureActive()
                val out = LocalPipeline.render(
                    BitmapIO.toPixels(source), analysis, active, request.faceIndex,
                    backgrounds = { name, w, h -> loadBackground(request.projectDir, name, w, h) },
                    checkCancelled = { job.ensureActive() },
                )
                BitmapIO.toBitmap(out)
            } catch (_: OutOfMemoryError) {
                throw EditException.OutOfMemory()
            }
        }
    }

    private fun loadBackground(dir: File?, name: String, w: Int, h: Int): IntArray? {
        val file = File(dir ?: return null, File(name).name)
        if (!file.exists()) return null
        return try {
            val bmp = BitmapIO.decode(file, maxOf(w, h).coerceAtLeast(64) * 2, 8_000_000L)
            try { BitmapIO.centerCropPixels(bmp, w, h) } finally { bmp.recycle() }
        } catch (_: Exception) {
            null
        }
    }
}
