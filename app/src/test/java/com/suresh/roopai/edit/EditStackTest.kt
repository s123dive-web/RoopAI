package com.suresh.roopai.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditStackTest {
    private fun adj(tool: Tool, amount: Float, color: Int? = null, variant: String? = null) = EditOp.Adjust(tool, amount, color, variant)
    private fun cloud(id: String, file: String = "$id.jpg") = EditOp.CloudEdit(id, id, "prompt $id", "gemini", "m", file)

    @Test
    fun adjustAppendsThenUpdatesInPlace() {
        var s = EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f)).withAdjust(adj(Tool.FACE_SLIM, 0.2f))
        s = s.withAdjust(adj(Tool.SKIN_SMOOTH, 0.8f))
        assertEquals(2, s.ops.size)
        assertEquals(adj(Tool.SKIN_SMOOTH, 0.8f), s.ops[0])
        assertEquals(0.8f, s.adjust(Tool.SKIN_SMOOTH)!!.amount, 0f)
    }

    @Test
    fun neutralAmountRemovesTheOp() {
        val s = EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f)).withAdjust(adj(Tool.SKIN_SMOOTH, 0f))
        assertTrue(s.isEmpty)
        assertTrue(EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0f)).isEmpty)
        assertTrue(EditStack().withAdjust(adj(Tool.FACE_SLIM, -0.4f)).withoutTool(Tool.FACE_SLIM).isEmpty)
    }

    @Test
    fun opsBeforeACloudEditAreFrozen() {
        var s = EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f)).withCloud(cloud("beard"))
        assertNull(s.adjust(Tool.SKIN_SMOOTH))
        s = s.withAdjust(adj(Tool.SKIN_SMOOTH, 0.9f))
        assertEquals(3, s.ops.size)
        assertEquals(0.3f, (s.ops[0] as EditOp.Adjust).amount, 0f)
        assertEquals(0.9f, s.adjust(Tool.SKIN_SMOOTH)!!.amount, 0f)
        assertEquals(1, s.activeOps.size)
    }

    @Test
    fun geometryIsASingletonPerSegment() {
        var s = EditStack().withGeometry(EditOp.Geometry(quarterTurns = 1))
        s = s.withGeometry(EditOp.Geometry(quarterTurns = 2, flipH = true))
        assertEquals(1, s.ops.size)
        assertEquals(2, s.geometry!!.quarterTurns)
        s = s.withGeometry(EditOp.Geometry())
        assertTrue(s.isEmpty)
        assertNull(s.geometry)
    }

    @Test
    fun geometryFlags() {
        assertTrue(EditOp.Geometry().isIdentity)
        assertTrue(EditOp.Geometry(quarterTurns = 4).isIdentity)
        assertFalse(EditOp.Geometry(straighten = 2f).isIdentity)
        val g = EditOp.Geometry(cropL = 0.1f)
        assertTrue(g.hasCrop); assertFalse(g.isIdentity)
        assertFalse(g.withoutCrop().hasCrop)
    }

    @Test
    fun segmentsSplitAtCloudEdits() {
        val s = EditStack()
            .withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f))
            .withCloud(cloud("a"))
            .withCloud(cloud("b"))
            .withAdjust(adj(Tool.FILTER, 1f, variant = "mono"))
        val seg = s.segments()
        assertEquals(3, seg.size)
        assertNull(seg[0].base); assertEquals(1, seg[0].locals.size)
        assertEquals("a", seg[1].base!!.presetId); assertTrue(seg[1].locals.isEmpty())
        assertEquals("b", seg[2].base!!.presetId); assertEquals(1, seg[2].locals.size)
        assertEquals(1, EditStack().segments().size)
    }

    @Test
    fun referencedFilesListsCloudResultsAndBackgroundPhotos() {
        val s = EditStack()
            .withCloud(cloud("a", "cloud_1.jpg"))
            .withAdjust(adj(Tool.BG_REPLACE, 1f, variant = EditStack.PHOTO_PREFIX + "bg_2.jpg"))
            .withAdjust(adj(Tool.FILTER, 1f, variant = "mono"))
        assertEquals(setOf("cloud_1.jpg", "bg_2.jpg"), s.referencedFiles())
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        val s = EditStack(faceIndex = 2)
            .withAdjust(adj(Tool.MAKEUP_LIPSTICK, 0.7f, 0xFFB3202C.toInt()))
            .withGeometry(EditOp.Geometry(1, true, false, -3.5f, 0.1f, 0.2f, 0.9f, 0.8f))
            .withCloud(cloud("wedding"))
            .withAdjust(adj(Tool.ACC_GLASSES, 0.5f, 0xFF101012.toInt(), "aviator"))
        assertEquals(s, EditStack.fromJson(s.toJson()))
    }

    @Test
    fun corruptOrUnknownJsonLoadsAsEmptyInsteadOfCrashing() {
        assertEquals(EditStack(), EditStack.fromJson("not json"))
        assertEquals(EditStack(), EditStack.fromJson(""))
        assertEquals(EditStack(), EditStack.fromJson("""{"ops":[{"type":"adjust","tool":"TOOL_FROM_THE_FUTURE","amount":1.0}]}"""))
        assertEquals(1, EditStack.fromJson("""{"ops":[],"faceIndex":1,"somethingNew":true}""").faceIndex)
    }

    @Test
    fun historyUndoRedo() {
        val s1 = EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f))
        val s2 = s1.withAdjust(adj(Tool.FACE_SLIM, 0.5f))
        var h = EditHistory().push(s1).push(s2)
        assertTrue(h.canUndo); assertFalse(h.canRedo)
        h = h.undo()
        assertEquals(s1, h.present); assertTrue(h.canRedo)
        h = h.undo()
        assertEquals(EditStack(), h.present); assertFalse(h.canUndo)
        assertSame(h, h.undo())
        h = h.redo().redo()
        assertEquals(s2, h.present)
        assertSame(h, h.redo())
    }

    @Test
    fun pushingAfterUndoDropsTheRedoBranch() {
        val s1 = EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f))
        val h = EditHistory().push(s1).undo().push(EditStack().withAdjust(adj(Tool.SKIN_TONE, 1f)))
        assertFalse(h.canRedo)
        assertEquals(1, h.past.size)
    }

    @Test
    fun sliderDragCoalescesIntoOneUndoStep() {
        var h = EditHistory()
        for (i in 1..10) h = h.push(EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, i / 10f)), key = "SKIN_SMOOTH")
        assertEquals(1, h.past.size)
        assertEquals(1f, h.present.adjust(Tool.SKIN_SMOOTH)!!.amount, 0f)
        h = h.seal().push(h.present.withAdjust(adj(Tool.SKIN_SMOOTH, 0.5f)), key = "SKIN_SMOOTH")
        assertEquals(2, h.past.size)
        h = h.undo()
        assertEquals(1f, h.present.adjust(Tool.SKIN_SMOOTH)!!.amount, 0f)
        assertEquals(EditStack(), h.undo().present)
    }

    @Test
    fun differentKeysAreSeparateSteps() {
        val h = EditHistory()
            .push(EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.5f)), "a")
            .push(EditStack().withAdjust(adj(Tool.SKIN_SMOOTH, 0.5f)).withAdjust(adj(Tool.SKIN_TONE, 0.5f)), "b")
        assertEquals(2, h.past.size)
    }

    @Test
    fun pushingTheSameStackIsANoOpAndResetIsUndoable() {
        val s1 = EditStack(faceIndex = 1).withAdjust(adj(Tool.SKIN_SMOOTH, 0.3f))
        val h = EditHistory().push(s1)
        assertSame(h, h.push(s1))
        val r = h.reset()
        assertTrue(r.present.isEmpty)
        assertEquals("reset keeps the chosen face", 1, r.present.faceIndex)
        assertEquals(s1, r.undo().present)
    }

    @Test
    fun historyIsBounded() {
        var h = EditHistory()
        for (i in 1..(EditHistory.MAX_STEPS + 25)) h = h.push(EditStack(faceIndex = i))
        assertEquals(EditHistory.MAX_STEPS, h.past.size)
    }

    @Test
    fun toolCatalogueCoversEveryLocalCategory() {
        for (c in Category.entries.filter { !it.cloudOnly }) assertTrue(c.name, Tool.of(c).isNotEmpty())
        for (c in Category.entries.filter { it.cloudOnly }) assertTrue(c.name, Tool.of(c).isEmpty())
        for (t in Tool.entries) {
            assertTrue(t.name, t.defaultAmount != 0f && t.defaultAmount in t.minAmount..1f)
            if (t.name.startsWith("ACC_")) assertTrue(t.name, t.variants.isNotEmpty() && t.hasColor)
        }
        assertEquals(10, Category.entries.count { it.ordinal < 10 })
    }
}
