package com.suresh.roopai.data

import com.suresh.roopai.data.db.EditDao
import com.suresh.roopai.data.db.EditRecord
import com.suresh.roopai.edit.EditStack
import kotlinx.coroutines.flow.Flow

/** Persistence of edit projects: Room row + project folder. */
class HistoryRepository(
    private val dao: EditDao,
    private val files: ProjectFiles,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** File operations the repository needs; implemented by [ProjectStore]. */
    interface ProjectFiles {
        suspend fun delete(projectId: String)
        suspend fun deleteAll()
        suspend fun prune(projectId: String, keep: Set<String>)
    }

    val edits: Flow<List<EditRecord>> = dao.observeAll()

    suspend fun create(projectId: String) {
        val now = clock()
        dao.upsert(EditRecord(projectId, now, now, EditStack().toJson()))
    }

    suspend fun load(projectId: String): EditStack? = dao.get(projectId)?.let { EditStack.fromJson(it.stackJson) }

    suspend fun exists(projectId: String): Boolean = dao.get(projectId) != null

    /** Saves the stack. Returns false when the project no longer exists (deleted meanwhile). */
    suspend fun save(projectId: String, stack: EditStack): Boolean =
        dao.updateStack(projectId, stack.toJson(), stack.ops.size, clock()) > 0

    suspend fun markExported(projectId: String, uri: String) = dao.setExported(projectId, uri, clock())

    /** Drops cached files the saved stack does not reference (e.g. AI results that were undone). */
    suspend fun prune(projectId: String) {
        val stack = load(projectId) ?: return
        files.prune(projectId, stack.referencedFiles())
    }

    suspend fun delete(projectId: String) {
        dao.delete(projectId)
        files.delete(projectId)
    }

    suspend fun clear() {
        dao.deleteAll()
        files.deleteAll()
    }
}
