package com.suresh.roopai.edit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ordered edit operations plus which detected face the face tools apply to. */
@Serializable
data class EditStack(
    val ops: List<EditOp> = emptyList(),
    val faceIndex: Int = 0,
) {
    val isEmpty get() = ops.isEmpty()

    private val lastCloudIndex get() = ops.indexOfLast { it is EditOp.CloudEdit }

    /** Ops after the last cloud edit; the only ones that can still be tweaked in place. */
    val activeOps: List<EditOp> get() = ops.subList(lastCloudIndex + 1, ops.size)

    fun adjust(tool: Tool): EditOp.Adjust? =
        activeOps.firstOrNull { it is EditOp.Adjust && it.tool == tool } as EditOp.Adjust?

    val geometry: EditOp.Geometry? get() = activeOps.firstOrNull { it is EditOp.Geometry } as EditOp.Geometry?

    /**
     * Sets a tool. If the tool is already present after the last cloud edit it is updated in
     * place (keeps its position); a neutral value removes it; otherwise it is appended.
     */
    fun withAdjust(op: EditOp.Adjust): EditStack {
        val start = lastCloudIndex + 1
        val idx = (start until ops.size).firstOrNull { (ops[it] as? EditOp.Adjust)?.tool == op.tool }
        val out = ops.toMutableList()
        when {
            idx != null && op.isNeutral -> out.removeAt(idx)
            idx != null -> out[idx] = op
            !op.isNeutral -> out.add(op)
        }
        return copy(ops = out)
    }

    fun withoutTool(tool: Tool): EditStack = withAdjust(EditOp.Adjust(tool, 0f))

    fun withGeometry(g: EditOp.Geometry): EditStack {
        val start = lastCloudIndex + 1
        val idx = (start until ops.size).firstOrNull { ops[it] is EditOp.Geometry }
        val out = ops.toMutableList()
        when {
            idx != null && g.isIdentity -> out.removeAt(idx)
            idx != null -> out[idx] = g
            !g.isIdentity -> out.add(g)
        }
        return copy(ops = out)
    }

    fun withCloud(op: EditOp.CloudEdit): EditStack = copy(ops = ops + op)

    fun withFace(index: Int): EditStack = copy(faceIndex = index)

    /** Splits the list at cloud edits: each segment has a base (null = original) and local ops. */
    fun segments(): List<Segment> {
        val out = ArrayList<Segment>()
        var base: EditOp.CloudEdit? = null
        var locals = ArrayList<EditOp>()
        for (op in ops) {
            if (op is EditOp.CloudEdit) {
                out.add(Segment(base, locals))
                base = op
                locals = ArrayList()
            } else {
                locals.add(op)
            }
        }
        out.add(Segment(base, locals))
        return out
    }

    /** Result files referenced by this stack (used to garbage-collect project folders). */
    fun referencedFiles(): Set<String> = buildSet {
        for (op in ops) {
            if (op is EditOp.CloudEdit && op.resultFile.isNotEmpty()) add(op.resultFile)
            if (op is EditOp.Adjust && op.tool == Tool.BG_REPLACE && op.variant?.startsWith(PHOTO_PREFIX) == true) {
                add(op.variant.removePrefix(PHOTO_PREFIX))
            }
        }
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    data class Segment(val base: EditOp.CloudEdit?, val locals: List<EditOp>)

    companion object {
        const val PHOTO_PREFIX = "photo:"
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** Never throws: a corrupt or future-format stack loads as empty so the photo still opens. */
        fun fromJson(json: String): EditStack = try {
            JSON.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            EditStack()
        }
    }
}

/**
 * Undo / redo over immutable [EditStack] snapshots. Consecutive changes with the same
 * [coalesceKey] (a slider being dragged) collapse into one undo step.
 */
data class EditHistory(
    val past: List<EditStack> = emptyList(),
    val present: EditStack = EditStack(),
    val future: List<EditStack> = emptyList(),
    val coalesceKey: String? = null,
) {
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun push(next: EditStack, key: String? = null): EditHistory {
        if (next == present) return this
        return if (key != null && key == coalesceKey && past.isNotEmpty()) {
            copy(present = next, future = emptyList())
        } else {
            copy(past = (past + present).takeLast(MAX_STEPS), present = next, future = emptyList(), coalesceKey = key)
        }
    }

    /** Ends a coalescing run so the next change becomes its own undo step. */
    fun seal(): EditHistory = if (coalesceKey == null) this else copy(coalesceKey = null)

    fun undo(): EditHistory =
        if (!canUndo) this else EditHistory(past.dropLast(1), past.last(), listOf(present) + future)

    fun redo(): EditHistory =
        if (!canRedo) this else EditHistory(past + present, future.first(), future.drop(1))

    fun reset(): EditHistory = push(EditStack(faceIndex = present.faceIndex))

    companion object {
        const val MAX_STEPS = 60
    }
}
