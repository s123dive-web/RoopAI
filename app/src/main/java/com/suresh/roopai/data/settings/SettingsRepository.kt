package com.suresh.roopai.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.suresh.roopai.data.image.OutputFormat
import com.suresh.roopai.engine.cloud.CloudConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val title: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** Export quality; also decides how large the image sent to the AI provider is. */
enum class OutputQuality(val title: String, val note: String, val uploadLongSide: Int) {
    STANDARD("Standard", "JPEG 90, smaller files", 1024),
    HIGH("High", "JPEG 95", 1536),
    MAXIMUM("Maximum", "JPEG 100, largest files", 2048),
    LOSSLESS("Lossless", "PNG, very large files", 2048),
    ;

    val format: OutputFormat
        get() = when (this) {
            STANDARD -> OutputFormat.Jpeg(90)
            HIGH -> OutputFormat.Jpeg(95)
            MAXIMUM -> OutputFormat.Jpeg(100)
            LOSSLESS -> OutputFormat.Png
        }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val quality: OutputQuality = OutputQuality.HIGH,
    val providerId: String = "",
    /** Empty = provider default. */
    val model: String = "",
    val cloudConsent: Boolean = false,
)

/** Non-secret preferences in DataStore; the API key lives in [SecretStore]. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val secrets: SecretStore,
) {
    val settings: Flow<AppSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AppSettings(
                theme = enumOr(p[THEME], ThemeMode.SYSTEM),
                quality = enumOr(p[QUALITY], OutputQuality.HIGH),
                providerId = p[PROVIDER].orEmpty(),
                model = p[MODEL].orEmpty(),
                cloudConsent = p[CONSENT] ?: false,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setQuality(q: OutputQuality) { store.edit { it[QUALITY] = q.name } }
    suspend fun setProvider(id: String) { store.edit { it[PROVIDER] = id; it.remove(MODEL) } }
    suspend fun setModel(model: String) { store.edit { it[MODEL] = model.trim() } }
    suspend fun setConsent(given: Boolean) { store.edit { it[CONSENT] = given } }

    suspend fun apiKey(providerId: String): String? = secrets.get(keyName(providerId))
    suspend fun setApiKey(providerId: String, key: String?) = secrets.put(keyName(providerId), key)
    suspend fun hasApiKey(providerId: String): Boolean = !apiKey(providerId).isNullOrBlank()

    /** Snapshot for [com.suresh.roopai.engine.cloud.CloudEngine]. */
    suspend fun cloudConfig(defaultProviderId: String): CloudConfig {
        val s = current()
        val provider = s.providerId.ifBlank { defaultProviderId }
        return CloudConfig(provider, s.model, apiKey(provider), s.cloudConsent, s.quality.uploadLongSide)
    }

    private fun keyName(providerId: String) = "api_key_$providerId"

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val QUALITY = stringPreferencesKey("quality")
        val PROVIDER = stringPreferencesKey("provider")
        val MODEL = stringPreferencesKey("model")
        val CONSENT = booleanPreferencesKey("cloud_consent")
    }
}
