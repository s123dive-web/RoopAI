package com.suresh.roopai.engine.cloud

import android.graphics.Bitmap
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.FaceEditEngine
import com.suresh.roopai.edit.RenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the cloud engine needs to know at call time; read fresh from settings for each edit. */
data class CloudConfig(
    val providerId: String,
    val model: String,
    val apiKey: String?,
    val consentGiven: Boolean,
    val uploadLongSide: Int,
)

/** Retry with exponential backoff for transient failures. */
class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1500,
    val factor: Double = 2.0,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    fun isRetryable(e: EditException): Boolean = when (e) {
        is EditException.Server -> e.code == 0 || e.code >= 500
        is EditException.QuotaExceeded -> true // short rate limits usually clear within seconds
        is EditException.Timeout -> false       // already waited long; let the user decide
        else -> false
    }

    suspend fun <T> run(block: suspend (attempt: Int) -> T): T {
        var wait = baseDelayMs
        var attempt = 1
        while (true) {
            try {
                return block(attempt)
            } catch (e: EditException) {
                if (attempt >= maxAttempts || !isRetryable(e)) throw e
                sleep(wait)
                wait = (wait * factor).toLong()
                attempt++
            }
        }
    }
}

/**
 * Optional bring-your-own-key engine. The only component that uses the network, and only after
 * the user has stored a key and accepted the upload consent.
 */
class CloudEngine(
    private val registry: ProviderRegistry,
    private val config: suspend () -> CloudConfig,
    private val retry: RetryPolicy = RetryPolicy(),
    private val encode: (Bitmap, Int) -> ByteArray = { bmp, longSide ->
        val scaled = BitmapIO.scaleToFit(bmp, longSide)
        try { BitmapIO.jpegBytes(scaled, 92) } finally { if (scaled !== bmp) scaled.recycle() }
    },
    private val decode: (ByteArray) -> Bitmap = BitmapIO::decodeBytes,
) : FaceEditEngine {
    override val id = "cloud"
    override val isOffline = false

    override fun supports(op: EditOp) = op is EditOp.CloudEdit

    /** Result of the last successful call (raw bytes so the caller can cache them unchanged). */
    class Output(val bitmap: Bitmap, val bytes: ByteArray, val mimeType: String, val providerId: String, val model: String, val note: String?)

    override suspend fun apply(source: Bitmap, ops: List<EditOp>, request: RenderRequest): Bitmap {
        var current = source
        for (op in ops) {
            require(op is EditOp.CloudEdit) { "CloudEngine only handles cloud edits" }
            val out = edit(current, op.prompt).bitmap
            if (current !== source) current.recycle()
            current = out
        }
        return current
    }

    /** Sends [source] with [prompt] to the configured provider. */
    suspend fun edit(source: Bitmap, prompt: String): Output {
        val cfg = config()
        val key = cfg.apiKey?.takeIf { it.isNotBlank() } ?: throw EditException.NoApiKey()
        if (!cfg.consentGiven) throw EditException.ConsentRequired()
        val provider = registry.get(cfg.providerId)
        val model = cfg.model.ifBlank { provider.defaultModel }
        val bytes = withContext(Dispatchers.Default) {
            try { encode(source, cfg.uploadLongSide) } catch (_: OutOfMemoryError) { throw EditException.OutOfMemory() }
        }
        val result = retry.run { provider.edit(ImageEditRequest(key, model, prompt, bytes)) }
        val bitmap = withContext(Dispatchers.Default) { decode(result.imageBytes) }
        return Output(bitmap, result.imageBytes, result.mimeType, provider.id, model, result.note)
    }

    suspend fun validateKey(providerId: String, apiKey: String) {
        if (apiKey.isBlank()) throw EditException.NoApiKey()
        registry.get(providerId).validateKey(apiKey.trim())
    }
}
