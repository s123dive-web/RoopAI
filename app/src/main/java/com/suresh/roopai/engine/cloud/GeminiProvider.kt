package com.suresh.roopai.engine.cloud

import android.util.Base64
import com.suresh.roopai.edit.EditException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gemini native image editing ("Nano Banana" models) through the stateless
 * `models/{model}:generateContent` REST call. Model IDs and request shape were verified against
 * Google's own repositories on 2026-11-23 (see RESEARCH.md).
 */
class GeminiProvider(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val base64Encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) },
    private val base64Decode: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) },
) : ImageEditProvider {
    override val id = ID
    override val displayName = "Google Gemini"
    override val models = listOf(
        ModelInfo("gemini-3.1-flash-lite-image", "Nano Banana 2 Lite", "Fastest, lowest cost, has a free tier"),
        ModelInfo("gemini-3.1-flash-image", "Nano Banana 2", "Balanced speed and quality"),
        ModelInfo("gemini-nano-banana-2.1", "Nano Banana 2.1", "Higher fidelity"),
        ModelInfo("gemini-3-pro-image-preview", "Nano Banana Pro (preview)", "Best quality, slowest, paid"),
    )
    override val defaultModel = models.first().id
    override val keyHelp = "Create a free key in Google AI Studio (aistudio.google.com) under \"Get API key\"."

    override suspend fun edit(request: ImageEditRequest): ImageEditResult = withContext(Dispatchers.IO) {
        val http = Request.Builder()
            .url("$baseUrl/models/${request.model}:generateContent")
            .header("x-goog-api-key", request.apiKey)
            .post(buildBody(request).toString().toRequestBody(JSON_TYPE))
            .build()
        client.newCall(http).await().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) throw mapError(resp.code, text, request.model)
            parseResult(text)
        }
    }

    override suspend fun validateKey(apiKey: String) = withContext(Dispatchers.IO) {
        val http = Request.Builder().url("$baseUrl/models?pageSize=1").header("x-goog-api-key", apiKey).get().build()
        client.newCall(http).await().use { resp ->
            if (!resp.isSuccessful) throw mapError(resp.code, resp.body.string(), "")
        }
    }

    internal fun buildBody(request: ImageEditRequest): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", request.prompt) })
                        add(
                            buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", request.mimeType)
                                    put("data", base64Encode(request.imageBytes))
                                }
                            },
                        )
                    }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("responseModalities", buildJsonArray { add(JsonPrimitive("TEXT")); add(JsonPrimitive("IMAGE")) })
        }
    }

    internal fun parseResult(body: String): ImageEditResult {
        val root = try {
            LENIENT.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw EditException.BadResponse("unreadable response")

        val candidates = root["candidates"] as? JsonArray
        val first = candidates?.firstOrNull() as? JsonObject
        val parts = ((first?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        var note: String? = null
        for (p in parts) {
            val obj = p as? JsonObject ?: continue
            obj.string("text")?.let { if (it.isNotBlank()) note = it.trim() }
            val inline = (obj["inlineData"] ?: obj["inline_data"]) as? JsonObject ?: continue
            val data = inline.string("data") ?: continue
            val mime = inline.string("mimeType") ?: inline.string("mime_type") ?: "image/png"
            val bytes = try { base64Decode(data) } catch (_: Exception) { ByteArray(0) }
            if (bytes.isNotEmpty()) return ImageEditResult(bytes, mime, note)
        }
        val blockReason = (root["promptFeedback"] as? JsonObject)?.string("blockReason")
        val finish = first?.string("finishReason")
        when {
            blockReason != null -> throw EditException.Blocked(humanize(blockReason))
            finish != null && finish != "STOP" && finish != "MAX_TOKENS" -> throw EditException.Blocked(humanize(finish))
            else -> throw EditException.BadResponse(note?.take(160) ?: "")
        }
    }

    internal fun mapError(code: Int, body: String, model: String): EditException {
        val err = try {
            (LENIENT.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject
        } catch (_: Exception) {
            null
        }
        val message = err?.string("message").orEmpty()
        val status = err?.string("status").orEmpty()
        val reasons = (err?.get("details") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("reason") }
        val short = message.lineSequence().firstOrNull().orEmpty().take(140)
        val keyProblem = "API_KEY_INVALID" in reasons || message.contains("API key", ignoreCase = true)
        return when {
            code == 400 && keyProblem -> EditException.InvalidKey()
            code == 401 || code == 403 -> EditException.InvalidKey(short)
            code == 404 -> EditException.ModelNotFound(model)
            code == 429 || status == "RESOURCE_EXHAUSTED" -> EditException.QuotaExceeded(short)
            code == 400 -> EditException.Server(code, short)
            else -> EditException.Server(code, short)
        }
    }

    private fun humanize(reason: String) = reason.lowercase().replace('_', ' ')

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    companion object {
        const val ID = "gemini"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** Bridges OkHttp to coroutines; cancelling the coroutine cancels the HTTP call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isCancelled) return
            cont.resumeWithException(mapIo(e))
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, r, _ -> r.close() }
        }
    })
}

internal fun mapIo(e: IOException): EditException = when (e) {
    is UnknownHostException -> EditException.NoNetwork(e)
    is SocketTimeoutException -> EditException.Timeout(e)
    is InterruptedIOException -> EditException.Timeout(e)
    is java.net.ConnectException, is java.net.NoRouteToHostException -> EditException.NoNetwork(e)
    is javax.net.ssl.SSLException -> EditException.Server(0, "secure connection failed")
    else -> EditException.NoNetwork(e)
}
