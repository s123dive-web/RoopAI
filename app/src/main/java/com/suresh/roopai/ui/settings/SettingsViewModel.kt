package com.suresh.roopai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suresh.roopai.data.HistoryRepository
import com.suresh.roopai.data.settings.AppSettings
import com.suresh.roopai.data.settings.OutputQuality
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.data.settings.ThemeMode
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.engine.cloud.CloudEngine
import com.suresh.roopai.engine.cloud.ImageEditProvider
import com.suresh.roopai.engine.cloud.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Running : KeyCheck
    data object Valid : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val providers: List<ImageEditProvider> = emptyList(),
    val provider: ImageEditProvider? = null,
    /** Model id in effect (stored one, or the provider default). */
    val model: String = "",
    val hasKey: Boolean = false,
    /** Last four characters of the stored key, for recognition only. */
    val keyHint: String = "",
    val keyCheck: KeyCheck = KeyCheck.Idle,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val registry: ProviderRegistry,
    private val cloud: CloudEngine,
    private val history: HistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(providers = registry.providers))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { s ->
                val provider = registry.get(s.providerId)
                val key = repo.apiKey(provider.id)
                _state.update {
                    it.copy(
                        loaded = true, settings = s, provider = provider,
                        model = s.model.ifBlank { provider.defaultModel },
                        hasKey = !key.isNullOrBlank(), keyHint = hint(key),
                    )
                }
            }
        }
    }

    private fun hint(key: String?) = if (key.isNullOrBlank()) "" else "••••" + key.takeLast(4)

    fun setTheme(mode: ThemeMode) = launch { repo.setTheme(mode) }
    fun setQuality(q: OutputQuality) = launch { repo.setQuality(q) }
    fun setModel(model: String) = launch { if (model.isNotBlank()) repo.setModel(model) }

    fun setProvider(id: String) = launch {
        repo.setProvider(id)
        _state.update { it.copy(keyCheck = KeyCheck.Idle) }
    }

    fun saveKey(key: String) = launch {
        val provider = _state.value.provider ?: registry.default
        val clean = key.trim()
        if (clean.isEmpty()) {
            _state.update { it.copy(message = "Paste a key first.") }
            return@launch
        }
        repo.setApiKey(provider.id, clean)
        _state.update { it.copy(hasKey = true, keyHint = hint(clean), keyCheck = KeyCheck.Idle, message = "API key saved on this device.") }
    }

    fun removeKey() = launch {
        val provider = _state.value.provider ?: registry.default
        repo.setApiKey(provider.id, null)
        _state.update { it.copy(hasKey = false, keyHint = "", keyCheck = KeyCheck.Idle, message = "API key removed.") }
    }

    fun testKey() {
        val provider = _state.value.provider ?: registry.default
        if (_state.value.keyCheck == KeyCheck.Running) return
        _state.update { it.copy(keyCheck = KeyCheck.Running) }
        viewModelScope.launch {
            val result = try {
                val key = repo.apiKey(provider.id)
                if (key.isNullOrBlank()) throw EditException.NoApiKey()
                cloud.validateKey(provider.id, key)
                KeyCheck.Valid
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KeyCheck.Failed((e as? EditException)?.message ?: "The key could not be checked.")
            }
            _state.update { it.copy(keyCheck = result) }
        }
    }

    fun setConsent(given: Boolean) = launch { repo.setConsent(given) }

    fun clearHistory() = launch {
        history.clear()
        _state.update { it.copy(message = "History cleared.") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "That did not work: ${e.message ?: "unknown error"}") }
            }
        }
    }
}
