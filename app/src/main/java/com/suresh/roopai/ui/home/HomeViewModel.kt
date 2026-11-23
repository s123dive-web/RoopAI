package com.suresh.roopai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suresh.roopai.data.HistoryRepository
import com.suresh.roopai.data.db.EditRecord
import com.suresh.roopai.edit.EditException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Copies a picked or captured image into a new project and returns its id. */
fun interface PhotoImporter {
    suspend fun import(uri: String): String
}

data class ImportState(val importing: Boolean = false, val error: String? = null, val openProject: String? = null)

/** Shared by Home, Camera and History: the list of saved edits and the import action. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val importer: PhotoImporter,
) : ViewModel() {
    val edits: StateFlow<List<EditRecord>?> = history.edits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _import = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    fun import(uri: String) {
        if (_import.value.importing) return
        _import.value = ImportState(importing = true)
        viewModelScope.launch {
            try {
                val id = importer.import(uri)
                history.create(id)
                _import.value = ImportState(openProject = id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _import.value = ImportState(error = (e as? EditException)?.message ?: "This image could not be opened.")
            }
        }
    }

    fun importFailed(message: String) { _import.value = ImportState(error = message) }
    fun consumeNavigation() = _import.update { it.copy(openProject = null) }
    fun dismissError() = _import.update { it.copy(error = null) }

    fun delete(id: String) { viewModelScope.launch { history.delete(id) } }
    fun clearAll() { viewModelScope.launch { history.clear() } }
}
