package com.suresh.roopai.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suresh.roopai.core.imaging.Geometry
import com.suresh.roopai.edit.Category
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditHistory
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Needs
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.cloud.CloudPreset
import com.suresh.roopai.engine.cloud.CloudPresets
import com.suresh.roopai.engine.cloud.PromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which control panel is open under the image. */
sealed interface Panel {
    data object None : Panel
    data class ToolPanel(val tool: Tool) : Panel
    data object Crop : Panel
    data object Rotate : Panel
}

sealed interface EditorDialog {
    data object NeedApiKey : EditorDialog
    data class Consent(val pending: PendingCloud) : EditorDialog
    data class Error(val message: String, val offerSettings: Boolean = false) : EditorDialog
    data object ConfirmReset : EditorDialog
}

data class PendingCloud(val presetId: String, val title: String, val prompt: String)

sealed interface EditorEvent {
    data class Share(val uri: String, val mime: String) : EditorEvent
    data class Message(val text: String) : EditorEvent
}

enum class Busy { NONE, SAVING, SHARING }

data class EditorUiState(
    val loading: Boolean = true,
    val fatalError: String? = null,
    val original: PreviewFrame? = null,
    val preview: PreviewFrame? = null,
    val history: EditHistory = EditHistory(),
    val category: Category = Category.SKIN,
    val panel: Panel = Panel.None,
    val faces: List<FaceBox> = emptyList(),
    val facesKnown: Boolean = false,
    val rendering: Boolean = false,
    val comparing: Boolean = false,
    val cloudRunning: String? = null,
    val cloud: CloudStatus = CloudStatus(false, false, "", ""),
    val busy: Busy = Busy.NONE,
    val dialog: EditorDialog? = null,
    val event: EditorEvent? = null,
    val cropAspect: Float? = null,
) {
    val stack: EditStack get() = history.present
    val noFace: Boolean get() = facesKnown && faces.isEmpty()
    val isCropping: Boolean get() = panel == Panel.Crop
    val canExport: Boolean get() = !loading && fatalError == null && busy == Busy.NONE && cloudRunning == null
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val gateway: EditorGateway,
) : ViewModel() {
    private val projectId: String = savedState.get<String>(ARG_PROJECT).orEmpty()

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var renderJob: Job? = null
    private var persistJob: Job? = null
    private var cloudJob: Job? = null
    private var facesKey: String? = null

    init {
        viewModelScope.launch {
            try {
                val opened = gateway.open(projectId)
                _state.update { it.copy(loading = false, original = opened.original, preview = opened.original, history = EditHistory(present = opened.stack)) }
                refreshCloudStatus()
                refreshFaces()
                render()
                // gives a freshly imported photo its thumbnail for Home and History
                schedulePersist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, fatalError = e.userMessage()) }
            }
        }
    }

    // ------------------------------------------------------------ selection

    fun selectCategory(category: Category) {
        commitSlider()
        _state.update { it.copy(category = category, panel = Panel.None) }
        render()
    }

    fun selectTool(tool: Tool) {
        commitSlider()
        val s = _state.value
        if (s.panel == Panel.ToolPanel(tool)) return
        _state.update { it.copy(panel = Panel.ToolPanel(tool)) }
        // colour / style tools show their effect as soon as they are picked
        val pickLike = tool.hasColor || tool.variants.isNotEmpty()
        if (pickLike && s.stack.adjust(tool) == null && tool != Tool.BG_REPLACE && available(tool)) {
            apply(EditOp.Adjust(tool, tool.defaultAmount, tool.palette.firstOrNull(), tool.variants.firstOrNull()), key = null)
        } else {
            render()
        }
    }

    fun openCrop() {
        commitSlider()
        _state.update { it.copy(panel = Panel.Crop, cropAspect = null) }
        render()
    }

    fun openRotate() {
        commitSlider()
        _state.update { it.copy(panel = Panel.Rotate) }
        render()
    }

    fun closePanel() {
        commitSlider()
        _state.update { it.copy(panel = Panel.None) }
        render()
    }

    /** False when the on-device analysis cannot support the tool (no face found). */
    fun available(tool: Tool): Boolean = tool.needs != Needs.FACE || !_state.value.noFace

    // ---------------------------------------------------------------- edits

    fun setAmount(tool: Tool, amount: Float) {
        val current = _state.value.stack.adjust(tool)
        val a = amount.coerceIn(tool.minAmount, 1f).let { if (kotlin.math.abs(it) < 0.015f) 0f else it }
        apply(
            EditOp.Adjust(tool, a, current?.color ?: tool.palette.firstOrNull(), current?.variant ?: tool.variants.firstOrNull()),
            key = "amount:" + tool.name,
        )
    }

    fun setColor(tool: Tool, color: Int) {
        val current = _state.value.stack.adjust(tool)
        apply(EditOp.Adjust(tool, current?.amount ?: tool.defaultAmount, color, current?.variant ?: tool.variants.firstOrNull()), key = null)
    }

    fun setVariant(tool: Tool, variant: String) {
        val current = _state.value.stack.adjust(tool)
        apply(EditOp.Adjust(tool, current?.amount ?: tool.defaultAmount, current?.color ?: tool.palette.firstOrNull(), variant), key = null)
    }

    fun clearTool(tool: Tool) = push(_state.value.stack.withoutTool(tool), null)

    /** Ends a slider drag so the next change is a separate undo step. */
    fun commitSlider() = _state.update { it.copy(history = it.history.seal()) }

    private fun apply(op: EditOp.Adjust, key: String?) = push(_state.value.stack.withAdjust(op), key)

    private fun push(stack: EditStack, key: String?) {
        val before = _state.value.history
        val after = before.push(stack, key)
        if (after === before) return
        _state.update { it.copy(history = after) }
        onStackChanged()
    }

    fun undo() = changeHistory { it.undo() }
    fun redo() = changeHistory { it.redo() }

    fun askReset() {
        if (!_state.value.stack.isEmpty) _state.update { it.copy(dialog = EditorDialog.ConfirmReset) }
    }

    fun reset() {
        _state.update { it.copy(dialog = null, panel = Panel.None) }
        changeHistory { it.reset() }
    }

    private fun changeHistory(f: (EditHistory) -> EditHistory) {
        val before = _state.value.history
        val after = f(before)
        if (after === before) return
        _state.update { it.copy(history = after) }
        onStackChanged()
    }

    private fun onStackChanged() {
        refreshFaces()
        render()
        schedulePersist()
    }

    fun setComparing(on: Boolean) = _state.update { it.copy(comparing = on) }

    fun pickFace(index: Int) {
        val s = _state.value
        if (index !in s.faces.indices || index == s.stack.faceIndex) return
        push(s.stack.withFace(index), null)
    }

    // ------------------------------------------------------------- geometry

    private fun geometry() = _state.value.stack.geometry ?: EditOp.Geometry()

    fun rotate(quarterTurns: Int) {
        val g = geometry()
        // a rotation invalidates the crop rectangle, which is relative to the rotated frame
        push(_state.value.stack.withGeometry(g.withoutCrop().copy(quarterTurns = ((g.quarterTurns + quarterTurns) % 4 + 4) % 4)), null)
    }

    fun flipHorizontal() {
        val g = geometry()
        push(_state.value.stack.withGeometry(g.copy(flipH = !g.flipH, cropL = 1f - g.cropR, cropR = 1f - g.cropL)), null)
    }

    fun flipVertical() {
        val g = geometry()
        push(_state.value.stack.withGeometry(g.copy(flipV = !g.flipV, cropT = 1f - g.cropB, cropB = 1f - g.cropT)), null)
    }

    fun setStraighten(degrees: Float) {
        val d = degrees.coerceIn(-MAX_STRAIGHTEN, MAX_STRAIGHTEN).let { if (kotlin.math.abs(it) < 0.15f) 0f else it }
        push(_state.value.stack.withGeometry(geometry().copy(straighten = d)), "straighten")
    }

    fun setCrop(l: Float, t: Float, r: Float, b: Float) {
        val g = geometry().copy(cropL = l.coerceIn(0f, 1f), cropT = t.coerceIn(0f, 1f), cropR = r.coerceIn(0f, 1f), cropB = b.coerceIn(0f, 1f))
        push(_state.value.stack.withGeometry(g), "crop")
    }

    /** Applies a centred crop with [aspect] (width / height); null clears the crop. */
    fun setCropAspect(aspect: Float?) {
        commitSlider()
        val frame = _state.value.preview
        _state.update { it.copy(cropAspect = aspect) }
        if (aspect == null || frame == null) {
            push(_state.value.stack.withGeometry(geometry().withoutCrop()), null)
        } else {
            val c = Geometry.centeredAspectCrop(frame.width, frame.height, aspect)
            push(_state.value.stack.withGeometry(geometry().copy(cropL = c[0], cropT = c[1], cropR = c[2], cropB = c[3])), null)
        }
    }

    // ----------------------------------------------------------- background

    fun setBackgroundPhoto(uri: String) {
        viewModelScope.launch {
            try {
                val name = gateway.importBackground(projectId, uri)
                val current = _state.value.stack.adjust(Tool.BG_REPLACE)
                apply(EditOp.Adjust(Tool.BG_REPLACE, current?.amount ?: 1f, current?.color ?: Tool.BG_REPLACE.palette.first(), EditStack.PHOTO_PREFIX + name), key = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    // ---------------------------------------------------------------- cloud

    fun requestPreset(preset: CloudPreset) = requestCloud(PendingCloud(preset.id, preset.title, PromptBuilder.forPreset(preset)))

    fun requestCustom(text: String) {
        val prompt = PromptBuilder.forCustom(text)
        if (prompt == null) {
            emit(EditorEvent.Message("Describe the change you want first."))
            return
        }
        requestCloud(PendingCloud(CloudPresets.CUSTOM_ID, PromptBuilder.sanitize(text).take(40), prompt))
    }

    private fun requestCloud(pending: PendingCloud) {
        if (_state.value.cloudRunning != null) return
        commitSlider()
        viewModelScope.launch {
            val status = refreshCloudStatus()
            when {
                !status.hasKey -> _state.update { it.copy(dialog = EditorDialog.NeedApiKey) }
                !status.consentGiven -> _state.update { it.copy(dialog = EditorDialog.Consent(pending)) }
                else -> runCloud(pending)
            }
        }
    }

    fun acceptConsent(pending: PendingCloud) {
        _state.update { it.copy(dialog = null) }
        viewModelScope.launch {
            gateway.setConsent(true)
            refreshCloudStatus()
            runCloud(pending)
        }
    }

    private fun runCloud(pending: PendingCloud) {
        if (cloudJob?.isActive == true) return
        val stack = _state.value.stack
        _state.update { it.copy(cloudRunning = pending.title, panel = Panel.None) }
        cloudJob = viewModelScope.launch {
            try {
                val op = gateway.cloudEdit(projectId, stack, pending.presetId, pending.title, pending.prompt)
                // edits made while waiting are kept only if the stack is unchanged
                if (_state.value.stack == stack) {
                    push(stack.withCloud(op), null)
                } else {
                    emit(EditorEvent.Message("The photo changed while the AI edit was running; result discarded."))
                }
            } catch (e: CancellationException) {
                emit(EditorEvent.Message("AI edit cancelled."))
            } catch (e: Exception) {
                showError(e)
            } finally {
                _state.update { it.copy(cloudRunning = null) }
            }
        }
    }

    fun cancelCloud() {
        cloudJob?.cancel()
    }

    /** Re-reads key / consent state, e.g. after returning from Settings. */
    fun onResume() {
        viewModelScope.launch { refreshCloudStatus() }
    }

    private suspend fun refreshCloudStatus(): CloudStatus {
        val status = gateway.cloudStatus()
        _state.update { it.copy(cloud = status) }
        return status
    }

    // --------------------------------------------------------------- export

    fun save() = export(Busy.SAVING) {
        gateway.saveToGallery(projectId, _state.value.stack)
        emit(EditorEvent.Message("Saved to Pictures/RoopAI"))
    }

    fun share() = export(Busy.SHARING) {
        val payload = gateway.share(projectId, _state.value.stack)
        emit(EditorEvent.Share(payload.uri, payload.mime))
    }

    private fun export(kind: Busy, block: suspend () -> Unit) {
        if (!_state.value.canExport) return
        commitSlider()
        _state.update { it.copy(busy = kind) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            } finally {
                _state.update { it.copy(busy = Busy.NONE) }
            }
        }
    }

    fun storagePermissionDenied() =
        showError(IllegalStateException("Storage permission is needed to save photos on this Android version. You can still share the photo."))

    val needsStoragePermission: Boolean get() = gateway.needsStoragePermission

    // ------------------------------------------------------------ rendering

    private fun render() {
        val s = _state.value
        if (s.loading || s.fatalError != null) return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(rendering = true) }
            try {
                val frame = gateway.preview(projectId, s.stack, showFullFrame = s.isCropping)
                _state.update { it.copy(preview = frame, rendering = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(rendering = false) }
                showError(e)
            }
        }
    }

    private fun refreshFaces() {
        val stack = _state.value.stack
        val key = stack.segments().last().base?.resultFile ?: ""
        if (key == facesKey && _state.value.facesKnown) return
        facesKey = key
        viewModelScope.launch {
            try {
                val faces = gateway.faces(projectId, stack)
                _state.update { it.copy(faces = faces, facesKnown = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(faces = emptyList(), facesKnown = true) }
            }
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DELAY_MS)
            renderJob?.join()
            val s = _state.value
            try { gateway.persist(projectId, s.stack, s.preview.takeIf { !s.isCropping }) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    // --------------------------------------------------------------- dialog

    fun dismissDialog() = _state.update { it.copy(dialog = null) }
    fun consumeEvent() = _state.update { it.copy(event = null) }

    private fun emit(event: EditorEvent) = _state.update { it.copy(event = event) }

    private fun showError(e: Throwable) {
        val offer = e is EditException.InvalidKey || e is EditException.NoApiKey || e is EditException.ModelNotFound || e is EditException.QuotaExceeded
        _state.update { it.copy(dialog = EditorDialog.Error(e.userMessage(), offer)) }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is EditException -> message ?: "Something went wrong."
        is OutOfMemoryError -> "Not enough memory for this photo."
        else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
    }

    override fun onCleared() {
        val s = _state.value
        // viewModelScope is already cancelled here; finish saving on a detached scope
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            withContext(NonCancellable) {
                try {
                    if (!s.loading && s.fatalError == null) gateway.persist(projectId, s.stack, s.preview.takeIf { !s.isCropping })
                    gateway.close(projectId)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val ARG_PROJECT = "projectId"
        const val MAX_STRAIGHTEN = 15f
        const val PERSIST_DELAY_MS = 700L
    }
}
