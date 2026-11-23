package com.suresh.roopai.engine.cloud

/** A selectable model of a provider. */
data class ModelInfo(val id: String, val title: String, val note: String = "")

class ImageEditRequest(
    val apiKey: String,
    val model: String,
    val prompt: String,
    val imageBytes: ByteArray,
    val mimeType: String = "image/jpeg",
)

class ImageEditResult(val imageBytes: ByteArray, val mimeType: String, val note: String? = null)

/**
 * A cloud image-editing backend. To add another provider implement this interface and add it to
 * [ProviderRegistry]; settings, consent, retry and UI need no changes.
 *
 * Implementations throw [com.suresh.roopai.edit.EditException] subclasses for expected failures
 * and must cancel their network call when the calling coroutine is cancelled.
 */
interface ImageEditProvider {
    val id: String
    val displayName: String
    val models: List<ModelInfo>
    val defaultModel: String

    /** Where the user creates a key; shown in Settings. */
    val keyHelp: String

    suspend fun edit(request: ImageEditRequest): ImageEditResult

    /** Cheap authenticated call used by the "Test key" button. Throws on failure. */
    suspend fun validateKey(apiKey: String)
}

class ProviderRegistry(val providers: List<ImageEditProvider>) {
    init { require(providers.isNotEmpty()) }

    val default: ImageEditProvider get() = providers.first()
    fun get(id: String?): ImageEditProvider = providers.firstOrNull { it.id == id } ?: default
}
