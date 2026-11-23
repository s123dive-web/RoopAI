package com.suresh.roopai.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.suresh.roopai.data.db.EditDao
import com.suresh.roopai.data.db.EditRecord
import com.suresh.roopai.data.settings.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-ins shared by repository and ViewModel tests. */
class FakeEditDao : EditDao {
    val rows = MutableStateFlow<Map<String, EditRecord>>(emptyMap())

    override fun observeAll(): Flow<List<EditRecord>> = rows.map { m -> m.values.sortedByDescending { it.updatedAt } }
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun ids() = rows.value.keys.toList()
    override suspend fun upsert(record: EditRecord) { rows.value = rows.value + (record.id to record) }
    override suspend fun updateStack(id: String, stackJson: String, opCount: Int, now: Long): Int {
        val r = rows.value[id] ?: return 0
        rows.value = rows.value + (id to r.copy(stackJson = stackJson, opCount = opCount, updatedAt = now))
        return 1
    }
    override suspend fun setExported(id: String, uri: String, now: Long) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(exportedUri = uri, updatedAt = now)) }
    }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

class FakeProjectFiles : HistoryRepository.ProjectFiles {
    val deleted = ArrayList<String>()
    var deletedAll = 0
    val pruned = ArrayList<Pair<String, Set<String>>>()
    override suspend fun delete(projectId: String) { deleted.add(projectId) }
    override suspend fun deleteAll() { deletedAll++ }
    override suspend fun prune(projectId: String, keep: Set<String>) { pruned.add(projectId to keep) }
}

class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class FakeSecretStore : SecretStore {
    val values = HashMap<String, String>()
    override suspend fun get(name: String): String? = values[name]
    override suspend fun put(name: String, value: String?) {
        if (value.isNullOrBlank()) values.remove(name) else values[name] = value.trim()
    }
}
