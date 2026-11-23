package com.suresh.roopai.ui

import androidx.lifecycle.SavedStateHandle
import com.suresh.roopai.edit.Category
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.cloud.CloudPresets
import com.suresh.roopai.ui.editor.Busy
import com.suresh.roopai.ui.editor.CloudStatus
import com.suresh.roopai.ui.editor.EditorDialog
import com.suresh.roopai.ui.editor.EditorEvent
import com.suresh.roopai.ui.editor.EditorGateway
import com.suresh.roopai.ui.editor.EditorViewModel
import com.suresh.roopai.ui.editor.FaceBox
import com.suresh.roopai.ui.editor.Panel
import com.suresh.roopai.ui.editor.PreviewFrame
import com.suresh.roopai.ui.editor.SharePayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeGateway : EditorGateway {
        var stored = EditStack()
        var openError: Exception? = null
        var faceList = listOf(FaceBox(0.3f, 0.2f, 0.7f, 0.8f))
        var status = CloudStatus(hasKey = false, consentGiven = false, providerName = "Fake", model = "m")
        var cloudError: Exception? = null
        var cloudGate: CompletableDeferred<Unit>? = null
        var saveError: Exception? = null
        val previews = ArrayList<Pair<EditStack, Boolean>>()
        val persisted = ArrayList<EditStack>()
        val cloudCalls = ArrayList<String>()
        var faceCalls = 0
        var saved = 0
        var closed = 0
        override var needsStoragePermission = false

        override suspend fun open(projectId: String): EditorGateway.Opened {
            openError?.let { throw it }
            return EditorGateway.Opened(stored, PreviewFrame(null, 400, 300))
        }
        override suspend fun faces(projectId: String, stack: EditStack): List<FaceBox> { faceCalls++; return faceList }
        override suspend fun preview(projectId: String, stack: EditStack, showFullFrame: Boolean): PreviewFrame {
            previews.add(stack to showFullFrame)
            return PreviewFrame(null, 400, 300)
        }
        override suspend fun cloudEdit(projectId: String, stack: EditStack, presetId: String, title: String, prompt: String): EditOp.CloudEdit {
            cloudCalls.add(prompt)
            cloudGate?.await()
            cloudError?.let { throw it }
            return EditOp.CloudEdit(presetId, title, prompt, "fake", "m", "cloud_${cloudCalls.size}.jpg")
        }
        override suspend fun saveToGallery(projectId: String, stack: EditStack): String { saveError?.let { throw it }; saved++; return "content://x/1" }
        override suspend fun share(projectId: String, stack: EditStack) = SharePayload("content://share/1", "image/jpeg")
        override suspend fun persist(projectId: String, stack: EditStack, thumbnail: PreviewFrame?) { persisted.add(stack) }
        override suspend fun importBackground(projectId: String, uri: String) = "bg_1.img"
        override suspend fun cloudStatus() = status
        override suspend fun setConsent(given: Boolean) { status = status.copy(consentGiven = given) }
        override suspend fun close(projectId: String) { closed++ }
    }

    private val gw = FakeGateway()
    private fun vm() = EditorViewModel(SavedStateHandle(mapOf(EditorViewModel.ARG_PROJECT to "p1")), gw)

    @Test
    fun opensProjectLoadsFacesAndRendersOnce() = runTest {
        gw.stored = EditStack().withAdjust(EditOp.Adjust(Tool.SKIN_SMOOTH, 0.4f))
        val vm = vm()
        assertTrue(vm.state.value.loading)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.loading); assertNull(s.fatalError)
        assertEquals(gw.stored, s.stack)
        assertEquals(1, s.faces.size); assertTrue(s.facesKnown); assertFalse(s.noFace)
        assertNotNull(s.preview); assertNotNull(s.original)
        assertEquals(1, gw.previews.size)
        assertFalse(s.history.canUndo)
    }

    @Test
    fun openFailureShowsFatalErrorAndBlocksExport() = runTest {
        gw.openError = EditException.Unreadable("gone")
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value.fatalError!!.contains("could not be opened"))
        assertFalse(vm.state.value.canExport)
        vm.save(); advanceUntilIdle()
        assertEquals(0, gw.saved)
    }

    @Test
    fun sliderDragIsOneUndoStepAndRendersLatest() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.selectCategory(Category.SKIN); vm.selectTool(Tool.SKIN_SMOOTH)
        for (i in 1..8) vm.setAmount(Tool.SKIN_SMOOTH, i / 10f)
        vm.commitSlider()
        advanceUntilIdle()
        assertEquals(0.8f, vm.state.value.stack.adjust(Tool.SKIN_SMOOTH)!!.amount, 1e-6f)
        assertEquals(1, vm.state.value.history.past.size)
        assertEquals(vm.state.value.stack, gw.previews.last().first)
        vm.undo(); advanceUntilIdle()
        assertTrue(vm.state.value.stack.isEmpty)
        vm.redo(); advanceUntilIdle()
        assertEquals(0.8f, vm.state.value.stack.adjust(Tool.SKIN_SMOOTH)!!.amount, 1e-6f)
        assertFalse(vm.state.value.rendering)
    }

    @Test
    fun twoDragsAreTwoUndoSteps() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.setAmount(Tool.SKIN_SMOOTH, 0.3f); vm.setAmount(Tool.SKIN_SMOOTH, 0.5f); vm.commitSlider()
        vm.setAmount(Tool.SKIN_SMOOTH, 0.9f); vm.commitSlider()
        assertEquals(2, vm.state.value.history.past.size)
        vm.undo()
        assertEquals(0.5f, vm.state.value.stack.adjust(Tool.SKIN_SMOOTH)!!.amount, 1e-6f)
    }

    @Test
    fun amountSnapsToZeroNearCentreAndClamps() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.setAmount(Tool.FACE_SLIM, 0.01f)
        assertNull(vm.state.value.stack.adjust(Tool.FACE_SLIM))
        vm.setAmount(Tool.FACE_SLIM, -5f)
        assertEquals(-1f, vm.state.value.stack.adjust(Tool.FACE_SLIM)!!.amount, 0f)
        vm.setAmount(Tool.SKIN_SMOOTH, -0.5f)
        assertNull("unipolar tools cannot go negative", vm.state.value.stack.adjust(Tool.SKIN_SMOOTH))
    }

    @Test
    fun pickingAColourToolAppliesItsDefaultImmediately() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.selectTool(Tool.MAKEUP_LIPSTICK)
        val op = vm.state.value.stack.adjust(Tool.MAKEUP_LIPSTICK)!!
        assertEquals(Tool.MAKEUP_LIPSTICK.defaultAmount, op.amount, 0f)
        assertEquals(Tool.MAKEUP_LIPSTICK.palette.first(), op.color)
        assertEquals(Panel.ToolPanel(Tool.MAKEUP_LIPSTICK), vm.state.value.panel)
        // plain sliders do not auto-apply
        vm.selectTool(Tool.SKIN_SMOOTH)
        assertNull(vm.state.value.stack.adjust(Tool.SKIN_SMOOTH))
        // background replace waits for an explicit choice
        vm.selectTool(Tool.BG_REPLACE)
        assertNull(vm.state.value.stack.adjust(Tool.BG_REPLACE))
    }

    @Test
    fun colourAndVariantKeepTheOtherSettings() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.selectTool(Tool.ACC_GLASSES)
        vm.setAmount(Tool.ACC_GLASSES, 0.9f); vm.commitSlider()
        vm.setVariant(Tool.ACC_GLASSES, "aviator")
        vm.setColor(Tool.ACC_GLASSES, 0xFF123456.toInt())
        val op = vm.state.value.stack.adjust(Tool.ACC_GLASSES)!!
        assertEquals(0.9f, op.amount, 1e-6f); assertEquals("aviator", op.variant); assertEquals(0xFF123456.toInt(), op.color)
        vm.clearTool(Tool.ACC_GLASSES)
        assertNull(vm.state.value.stack.adjust(Tool.ACC_GLASSES))
    }

    @Test
    fun noFaceDisablesFaceToolsOnly() = runTest {
        gw.faceList = emptyList()
        val vm = vm(); advanceUntilIdle()
        assertTrue(vm.state.value.noFace)
        assertFalse(vm.available(Tool.SKIN_SMOOTH))
        assertTrue(vm.available(Tool.FILTER)); assertTrue(vm.available(Tool.BG_BLUR))
        vm.selectTool(Tool.MAKEUP_LIPSTICK)
        assertNull("no auto-apply when the tool cannot work", vm.state.value.stack.adjust(Tool.MAKEUP_LIPSTICK))
    }

    @Test
    fun multipleFacesCanBePickedAndResetKeepsTheChoice() = runTest {
        gw.faceList = listOf(FaceBox(0f, 0f, 0.4f, 0.5f), FaceBox(0.6f, 0f, 1f, 0.5f))
        val vm = vm(); advanceUntilIdle()
        vm.pickFace(1)
        assertEquals(1, vm.state.value.stack.faceIndex)
        vm.pickFace(7)
        assertEquals(1, vm.state.value.stack.faceIndex)
        vm.setAmount(Tool.SKIN_SMOOTH, 0.5f); vm.commitSlider()
        vm.askReset()
        assertEquals(EditorDialog.ConfirmReset, vm.state.value.dialog)
        vm.reset()
        assertTrue(vm.state.value.stack.isEmpty); assertEquals(1, vm.state.value.stack.faceIndex)
        vm.undo()
        assertNotNull("reset is undoable", vm.state.value.stack.adjust(Tool.SKIN_SMOOTH))
    }

    @Test
    fun resetOnAnUntouchedPhotoDoesNotAsk() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.askReset()
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun geometryRotateFlipStraightenAndCrop() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.rotate(1); vm.rotate(1); vm.rotate(-3)
        assertEquals(3, vm.state.value.stack.geometry!!.quarterTurns)
        vm.rotate(1)
        assertNull("full turn is identity and removes the op", vm.state.value.stack.geometry)
        vm.setCrop(0.1f, 0.2f, 0.6f, 0.9f); vm.commitSlider()
        vm.flipHorizontal()
        val g = vm.state.value.stack.geometry!!
        assertTrue(g.flipH); assertEquals(0.4f, g.cropL, 1e-6f); assertEquals(0.9f, g.cropR, 1e-6f)
        vm.setStraighten(40f)
        assertEquals(EditorViewModel.MAX_STRAIGHTEN, vm.state.value.stack.geometry!!.straighten, 0f)
        vm.setStraighten(0.05f)
        assertEquals(0f, vm.state.value.stack.geometry!!.straighten, 0f)
        vm.rotate(1)
        assertFalse("rotation clears the crop", vm.state.value.stack.geometry!!.hasCrop)
    }

    @Test
    fun cropModeRendersTheFullFrameAndAspectPresetCentres() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.selectCategory(Category.ADJUST); vm.openCrop(); advanceUntilIdle()
        assertTrue(vm.state.value.isCropping)
        assertTrue(gw.previews.last().second)
        vm.setCropAspect(1f); advanceUntilIdle()
        val g = vm.state.value.stack.geometry!!
        // frame is 400x300, so a square crop trims the sides
        assertEquals(0.125f, g.cropL, 1e-4f); assertEquals(0.875f, g.cropR, 1e-4f); assertEquals(0f, g.cropT, 0f)
        vm.setCropAspect(null)
        assertNull(vm.state.value.stack.geometry)
        vm.closePanel(); advanceUntilIdle()
        assertFalse(gw.previews.last().second)
    }

    @Test
    fun cloudWithoutKeyShowsAddKeyAndNeverCallsTheNetwork() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.requestPreset(CloudPresets.byId("beard_full")!!); advanceUntilIdle()
        assertEquals(EditorDialog.NeedApiKey, vm.state.value.dialog)
        assertTrue(gw.cloudCalls.isEmpty())
    }

    @Test
    fun cloudAsksConsentOnceThenRuns() = runTest {
        gw.status = gw.status.copy(hasKey = true)
        val vm = vm(); advanceUntilIdle()
        vm.requestPreset(CloudPresets.byId("beard_full")!!); advanceUntilIdle()
        val dialog = vm.state.value.dialog as EditorDialog.Consent
        assertTrue(gw.cloudCalls.isEmpty())
        vm.dismissDialog(); advanceUntilIdle()
        assertTrue("declining sends nothing", gw.cloudCalls.isEmpty()); assertFalse(gw.status.consentGiven)

        vm.requestPreset(CloudPresets.byId("beard_full")!!); advanceUntilIdle()
        vm.acceptConsent((vm.state.value.dialog as EditorDialog.Consent).pending); advanceUntilIdle()
        assertTrue(gw.status.consentGiven)
        assertEquals(1, gw.cloudCalls.size)
        assertTrue(gw.cloudCalls[0].contains("identity"))
        val op = vm.state.value.stack.ops.single() as EditOp.CloudEdit
        assertEquals("beard_full", op.presetId); assertEquals("cloud_1.jpg", op.resultFile)
        assertNull(vm.state.value.cloudRunning)
        assertEquals(dialog.pending.prompt, op.prompt)

        // second edit: no dialog any more
        vm.requestPreset(CloudPresets.byId("expr_smile")!!); advanceUntilIdle()
        assertNull(vm.state.value.dialog)
        assertEquals(2, vm.state.value.stack.ops.size)
        vm.undo(); advanceUntilIdle()
        assertEquals(1, vm.state.value.stack.ops.size)
    }

    @Test
    fun cloudErrorsBecomeDialogsAndLeaveTheStackUntouched() = runTest {
        gw.status = CloudStatus(true, true, "Fake", "m")
        val vm = vm(); advanceUntilIdle()
        gw.cloudError = EditException.InvalidKey()
        vm.requestPreset(CloudPresets.byId("bald")!!); advanceUntilIdle()
        var d = vm.state.value.dialog as EditorDialog.Error
        assertTrue(d.offerSettings); assertTrue(d.message.contains("API key"))
        assertTrue(vm.state.value.stack.isEmpty); assertNull(vm.state.value.cloudRunning)
        vm.dismissDialog()

        gw.cloudError = EditException.NoNetwork()
        vm.requestPreset(CloudPresets.byId("bald")!!); advanceUntilIdle()
        d = vm.state.value.dialog as EditorDialog.Error
        assertFalse(d.offerSettings); assertTrue(d.message.contains("internet"))
        vm.dismissDialog()

        gw.cloudError = EditException.QuotaExceeded()
        vm.requestPreset(CloudPresets.byId("bald")!!); advanceUntilIdle()
        assertTrue((vm.state.value.dialog as EditorDialog.Error).message.contains("Quota"))
    }

    @Test
    fun cloudCanBeCancelledAndBlocksASecondRequest() = runTest {
        gw.status = CloudStatus(true, true, "Fake", "m")
        gw.cloudGate = CompletableDeferred()
        val vm = vm(); advanceUntilIdle()
        vm.requestPreset(CloudPresets.byId("bald")!!); advanceUntilIdle()
        assertEquals("Bald preview", vm.state.value.cloudRunning)
        assertFalse(vm.state.value.canExport)
        vm.requestPreset(CloudPresets.byId("expr_smile")!!); advanceUntilIdle()
        assertEquals(1, gw.cloudCalls.size)
        vm.cancelCloud(); advanceUntilIdle()
        assertNull(vm.state.value.cloudRunning)
        assertTrue(vm.state.value.stack.isEmpty)
        assertEquals(EditorEvent.Message("AI edit cancelled."), vm.state.value.event)
    }

    @Test
    fun customPromptNeedsTextAndIsWrappedWithTheIdentityLock() = runTest {
        gw.status = CloudStatus(true, true, "Fake", "m")
        val vm = vm(); advanceUntilIdle()
        vm.requestCustom("   "); advanceUntilIdle()
        assertTrue(gw.cloudCalls.isEmpty())
        assertTrue(vm.state.value.event is EditorEvent.Message)
        vm.consumeEvent()
        vm.requestCustom("add gold earrings"); advanceUntilIdle()
        assertTrue(gw.cloudCalls.single().endsWith("Requested change: add gold earrings"))
        assertTrue(gw.cloudCalls.single().contains("skin tone"))
        assertEquals(CloudPresets.CUSTOM_ID, (vm.state.value.stack.ops.single() as EditOp.CloudEdit).presetId)
    }

    @Test
    fun facesAreReloadedOnlyWhenTheBaseImageChanges() = runTest {
        gw.status = CloudStatus(true, true, "Fake", "m")
        val vm = vm(); advanceUntilIdle()
        assertEquals(1, gw.faceCalls)
        vm.setAmount(Tool.SKIN_SMOOTH, 0.5f); vm.commitSlider(); advanceUntilIdle()
        assertEquals(1, gw.faceCalls)
        vm.requestPreset(CloudPresets.byId("bald")!!); advanceUntilIdle()
        assertEquals(2, gw.faceCalls)
        vm.undo(); advanceUntilIdle()
        assertEquals(3, gw.faceCalls)
    }

    @Test
    fun saveAndShareReportResults() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.save()
        assertEquals(Busy.SAVING, vm.state.value.busy)
        advanceUntilIdle()
        assertEquals(1, gw.saved); assertEquals(Busy.NONE, vm.state.value.busy)
        assertEquals(EditorEvent.Message("Saved to Pictures/RoopAI"), vm.state.value.event)
        vm.consumeEvent()
        vm.share(); advanceUntilIdle()
        assertEquals(EditorEvent.Share("content://share/1", "image/jpeg"), vm.state.value.event)
        vm.consumeEvent()
        gw.saveError = EditException.OutOfMemory()
        vm.save(); advanceUntilIdle()
        assertTrue((vm.state.value.dialog as EditorDialog.Error).message.contains("memory"))
        assertEquals(Busy.NONE, vm.state.value.busy)
    }

    @Test
    fun editsArePersistedAfterASettleDelay() = runTest {
        val vm = vm(); advanceUntilIdle()
        assertTrue(gw.persisted.isEmpty())
        vm.setAmount(Tool.SKIN_SMOOTH, 0.5f); vm.setAmount(Tool.SKIN_SMOOTH, 0.6f)
        advanceUntilIdle()
        assertEquals("debounced into one write", 1, gw.persisted.size)
        assertEquals(vm.state.value.stack, gw.persisted.last())
    }

    @Test
    fun backgroundPhotoBecomesAPhotoVariant() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.setBackgroundPhoto("content://picked/1"); advanceUntilIdle()
        val op = vm.state.value.stack.adjust(Tool.BG_REPLACE)!!
        assertEquals(EditStack.PHOTO_PREFIX + "bg_1.img", op.variant)
        assertEquals(setOf("bg_1.img"), vm.state.value.stack.referencedFiles())
    }

    @Test
    fun compareToggleAndStoragePermissionDenied() = runTest {
        val vm = vm(); advanceUntilIdle()
        vm.setComparing(true); assertTrue(vm.state.value.comparing)
        vm.setComparing(false); assertFalse(vm.state.value.comparing)
        vm.storagePermissionDenied()
        assertTrue((vm.state.value.dialog as EditorDialog.Error).message.contains("permission"))
    }
}
