package com.suresh.roopai.ui.editor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import com.suresh.roopai.data.HistoryRepository
import com.suresh.roopai.data.MediaSaver
import com.suresh.roopai.data.ProjectStore
import com.suresh.roopai.data.image.AtomicFiles
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.RenderRequest
import com.suresh.roopai.engine.cloud.CloudEngine
import com.suresh.roopai.engine.cloud.ProviderRegistry
import com.suresh.roopai.engine.local.LocalEngine
import com.suresh.roopai.engine.local.LocalPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorGatewayImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectStore,
    private val history: HistoryRepository,
    private val local: LocalEngine,
    private val cloud: CloudEngine,
    private val registry: ProviderRegistry,
    private val settings: SettingsRepository,
    private val saver: MediaSaver,
) : EditorGateway {

    /** The decoded preview-size base image; re-decoded only when the base file changes. */
    private class Base(val key: String, val bitmap: Bitmap)

    private var base: Base? = null
    private val baseLock = Mutex()

    override val needsStoragePermission get() = saver.needsLegacyPermission

    override suspend fun open(projectId: String): EditorGateway.Opened {
        val stack = history.load(projectId) ?: throw EditException.Unreadable("this edit no longer exists")
        val original = projects.original(projectId)
        if (!original.exists()) throw EditException.Unreadable("the original photo is missing")
        val bmp = withContext(Dispatchers.IO) { BitmapIO.decode(original, PREVIEW_LONG_SIDE) }
        return EditorGateway.Opened(usable(projectId, stack), PreviewFrame(bmp, bmp.width, bmp.height))
    }

    /** Drops ops whose cached AI result disappeared (e.g. storage cleared), keeping the rest. */
    private fun usable(projectId: String, stack: EditStack): EditStack {
        val missing = stack.ops.indexOfLast { it is EditOp.CloudEdit && !projects.file(projectId, it.resultFile).exists() }
        return if (missing < 0) stack else stack.copy(ops = stack.ops.drop(missing + 1))
    }

    private fun baseFile(projectId: String, stack: EditStack): File {
        val last = stack.segments().last().base
        return if (last == null) projects.original(projectId) else projects.file(projectId, last.resultFile)
    }

    private fun analysisKey(projectId: String, stack: EditStack) = projectId + "/" + baseFile(projectId, stack).name

    private suspend fun previewBase(projectId: String, stack: EditStack): Bitmap = baseLock.withLock {
        val file = baseFile(projectId, stack)
        val key = file.path
        base?.let { if (it.key == key) return it.bitmap }
        val bmp = withContext(Dispatchers.IO) { BitmapIO.decode(file, PREVIEW_LONG_SIDE) }
        base = Base(key, bmp)
        bmp
    }

    override suspend fun faces(projectId: String, stack: EditStack): List<FaceBox> {
        val bmp = previewBase(projectId, stack)
        return local.analyze(bmp, analysisKey(projectId, stack)).faces.map {
            val b = it.bounds
            FaceBox(b[0], b[1], b[2], b[3])
        }
    }

    private fun localOps(stack: EditStack, showFullFrame: Boolean): List<EditOp> {
        val ops = stack.segments().last().locals
        return if (!showFullFrame) ops else ops.map { if (it is EditOp.Geometry) it.withoutCrop() else it }
    }

    private fun request(projectId: String, stack: EditStack) =
        RenderRequest(analysisKey(projectId, stack), stack.faceIndex, projects.dir(projectId))

    override suspend fun preview(projectId: String, stack: EditStack, showFullFrame: Boolean): PreviewFrame {
        val src = previewBase(projectId, stack)
        val ops = localOps(stack, showFullFrame)
        val out = if (LocalPipeline.hasWork(ops)) local.apply(src, ops, request(projectId, stack)) else src
        return PreviewFrame(out, out.width, out.height)
    }

    /** Renders the stack from the full-resolution base, limited by [maxLongSide] and memory. */
    private suspend fun renderFull(projectId: String, stack: EditStack, maxLongSide: Int, maxPixels: Long): Bitmap {
        val file = baseFile(projectId, stack)
        val src = withContext(Dispatchers.IO) { BitmapIO.decode(file, maxLongSide, maxPixels) }
        val ops = localOps(stack, false)
        if (!LocalPipeline.hasWork(ops)) return src
        return try {
            local.apply(src, ops, request(projectId, stack))
        } finally {
            src.recycle()
        }
    }

    /** Export render with one retry at half the pixel budget if memory runs out. */
    private suspend fun renderForExport(projectId: String, stack: EditStack): Bitmap {
        val budget = exportPixelBudget()
        return try {
            renderFull(projectId, stack, Int.MAX_VALUE, budget)
        } catch (_: EditException.OutOfMemory) {
            System.gc()
            renderFull(projectId, stack, Int.MAX_VALUE, budget / 2)
        }
    }

    /**
     * The pipeline needs roughly 12 bytes of Java heap per pixel (source + result + scratch).
     * Staying under a third of the heap keeps a 50 MP photo from ever causing an OOM; it is
     * exported at the largest size this device can process instead.
     */
    private fun exportPixelBudget(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heap = minOf(Runtime.getRuntime().maxMemory(), am.largeMemoryClass * 1024L * 1024L)
        return (heap / 36).coerceIn(2_000_000L, 24_000_000L)
    }

    override suspend fun cloudEdit(projectId: String, stack: EditStack, presetId: String, title: String, prompt: String): EditOp.CloudEdit {
        val upload = settings.current().quality.uploadLongSide
        val src = renderFull(projectId, stack, upload, upload.toLong() * upload)
        val out = try {
            cloud.edit(src, prompt)
        } finally {
            src.recycle()
        }
        out.bitmap.recycle() // only the bytes are kept; previews decode them at their own size
        val ext = if (out.mimeType.contains("png")) "png" else if (out.mimeType.contains("webp")) "webp" else "jpg"
        val name = projects.addFile(projectId, "cloud", ext, out.bytes)
        return EditOp.CloudEdit(presetId, title, prompt, out.providerId, out.model, name)
    }

    override suspend fun saveToGallery(projectId: String, stack: EditStack): String {
        val bmp = renderForExport(projectId, stack)
        try {
            val uri = saver.saveToGallery(bmp, settings.current().quality.format)
            history.markExported(projectId, uri.toString())
            return uri.toString()
        } finally {
            bmp.recycle()
        }
    }

    override suspend fun share(projectId: String, stack: EditStack): SharePayload {
        val bmp = renderForExport(projectId, stack)
        try {
            val format = settings.current().quality.format
            return SharePayload(saver.prepareShare(bmp, format).toString(), format.mime)
        } finally {
            bmp.recycle()
        }
    }

    override suspend fun persist(projectId: String, stack: EditStack, thumbnail: PreviewFrame?) {
        if (!history.save(projectId, stack)) return
        thumbnail?.bitmap?.let { if (!it.isRecycled) projects.saveThumb(projectId, it) }
    }

    override suspend fun importBackground(projectId: String, uri: String): String = withContext(Dispatchers.IO) {
        val name = "bg_${System.currentTimeMillis()}.img"
        val target = projects.file(projectId, name)
        try {
            val input = context.contentResolver.openInputStream(uri.toUri()) ?: throw EditException.Unreadable("no access")
            input.use { src -> AtomicFiles.write(target) { src.copyTo(it) } }
            BitmapIO.info(target)
            name
        } catch (e: Exception) {
            target.delete()
            throw if (e is EditException) e else EditException.Unreadable(e.message ?: "")
        }
    }

    override suspend fun cloudStatus(): CloudStatus {
        val s = settings.current()
        val provider = registry.get(s.providerId)
        return CloudStatus(settings.hasApiKey(provider.id), s.cloudConsent, provider.displayName, s.model.ifBlank { provider.defaultModel })
    }

    override suspend fun setConsent(given: Boolean) = settings.setConsent(given)

    override suspend fun close(projectId: String) {
        baseLock.withLock { base = null }
        history.prune(projectId)
    }

    companion object {
        const val PREVIEW_LONG_SIDE = 1080
    }
}
