package com.suresh.roopai.cloud

import com.suresh.roopai.edit.EditException
import com.suresh.roopai.engine.cloud.GeminiProvider
import com.suresh.roopai.engine.cloud.ImageEditRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class GeminiProviderTest {
    private val png = byteArrayOf(1, 2, 3, 4, 5)
    private val pngB64 = Base64.getEncoder().encodeToString(png)

    private class Recorder(val code: Int, val body: String, val delayMs: Long = 0, val fail: java.io.IOException? = null) : Interceptor {
        var request: Request? = null
        var requestBody: String = ""
        var calls = 0
        override fun intercept(chain: Interceptor.Chain): Response {
            calls++
            request = chain.request()
            requestBody = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
            if (delayMs > 0) Thread.sleep(delayMs)
            fail?.let { throw it }
            return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun provider(rec: Recorder) = GeminiProvider(
        OkHttpClient.Builder().addInterceptor(rec).build(),
        baseUrl = "https://example.invalid/v1beta",
        base64Encode = { Base64.getEncoder().encodeToString(it) },
        base64Decode = { Base64.getDecoder().decode(it) },
    )

    private fun request(model: String = "gemini-3.1-flash-image") = ImageEditRequest("KEY123", model, "make it nice", byteArrayOf(9, 8, 7))

    private val ok = """{"candidates":[{"content":{"parts":[{"text":"Here you go"},{"inlineData":{"mimeType":"image/png","data":"$pngB64"}}]},"finishReason":"STOP"}]}"""

    @Test
    fun sendsKeyInHeaderAndImageInlineToTheModelEndpoint() = runBlocking {
        val rec = Recorder(200, ok)
        val result = provider(rec).edit(request())
        val req = rec.request!!
        assertEquals("https://example.invalid/v1beta/models/gemini-3.1-flash-image:generateContent", req.url.toString())
        assertEquals("KEY123", req.header("x-goog-api-key"))
        assertTrue("key must never be in the URL", !req.url.toString().contains("KEY123"))
        assertEquals("POST", req.method)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(rec.requestBody).jsonObject
        val parts = json["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("make it nice", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val inline = parts[1].jsonObject["inline_data"]!!.jsonObject
        assertEquals("image/jpeg", inline["mime_type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7)), inline["data"]!!.jsonPrimitive.content)
        val modalities = json["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("TEXT", "IMAGE"), modalities)
        assertArrayEquals(png, result.imageBytes)
        assertEquals("image/png", result.mimeType)
        assertEquals("Here you go", result.note)
    }

    @Test
    fun parsesSnakeCaseInlineData() {
        val body = """{"candidates":[{"content":{"parts":[{"inline_data":{"mime_type":"image/jpeg","data":"$pngB64"}}]}}]}"""
        val r = provider(Recorder(200, "")).parseResult(body)
        assertArrayEquals(png, r.imageBytes); assertEquals("image/jpeg", r.mimeType)
    }

    @Test
    fun blockedPromptAndSafetyStopsBecomeBlocked() {
        val p = provider(Recorder(200, ""))
        assertThrows<EditException.Blocked> { p.parseResult("""{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}""") }
        val e = assertThrows<EditException.Blocked> {
            p.parseResult("""{"candidates":[{"content":{"parts":[]},"finishReason":"IMAGE_SAFETY"}]}""")
        }
        assertTrue(e.message!!.contains("image safety"))
    }

    @Test
    fun textOnlyOrGarbageIsABadResponse() {
        val p = provider(Recorder(200, ""))
        val e = assertThrows<EditException.BadResponse> {
            p.parseResult("""{"candidates":[{"content":{"parts":[{"text":"I cannot edit this"}]},"finishReason":"STOP"}]}""")
        }
        assertTrue(e.message!!.contains("I cannot edit this"))
        assertThrows<EditException.BadResponse> { p.parseResult("<html>oops</html>") }
        assertThrows<EditException.BadResponse> { p.parseResult("{}") }
        assertThrows<EditException.BadResponse> { p.parseResult("""{"candidates":[{"content":{"parts":[{"inlineData":{"data":"@@not-base64@@"}}]}}]}""") }
    }

    @Test
    fun httpErrorsMapToSpecificExceptions() {
        val p = provider(Recorder(200, ""))
        val badKey = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
        assertTrue(p.mapError(400, badKey, "m") is EditException.InvalidKey)
        assertTrue(p.mapError(403, """{"error":{"message":"Permission denied","status":"PERMISSION_DENIED"}}""", "m") is EditException.InvalidKey)
        assertTrue(p.mapError(401, "", "m") is EditException.InvalidKey)
        val nf = p.mapError(404, """{"error":{"message":"models/x is not found"}}""", "x-model")
        assertTrue(nf is EditException.ModelNotFound && nf.message!!.contains("x-model"))
        assertTrue(p.mapError(429, """{"error":{"message":"Quota exceeded for metric","status":"RESOURCE_EXHAUSTED"}}""", "m") is EditException.QuotaExceeded)
        val server = p.mapError(503, """{"error":{"message":"The model is overloaded.","status":"UNAVAILABLE"}}""", "m")
        assertTrue(server is EditException.Server && server.code == 503 && server.message!!.contains("overloaded"))
        val bad = p.mapError(400, """{"error":{"message":"Invalid image","status":"INVALID_ARGUMENT"}}""", "m")
        assertTrue(bad is EditException.Server && bad.code == 400)
        assertTrue(p.mapError(500, "not json at all", "m") is EditException.Server)
    }

    @Test
    fun editThrowsMappedErrorForHttpFailure() = runBlocking {
        val rec = Recorder(429, """{"error":{"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""")
        try { provider(rec).edit(request()); fail("expected quota error") } catch (_: EditException.QuotaExceeded) { }
    }

    @Test
    fun networkFailuresAreClassified() = runBlocking {
        try { provider(Recorder(0, "", fail = java.net.UnknownHostException("nope"))).edit(request()); fail() } catch (_: EditException.NoNetwork) { }
        try { provider(Recorder(0, "", fail = java.net.SocketTimeoutException("slow"))).edit(request()); fail() } catch (_: EditException.Timeout) { }
        try { provider(Recorder(0, "", fail = java.net.ConnectException("refused"))).edit(request()); fail() } catch (_: EditException.NoNetwork) { }
    }

    @Test
    fun validateKeyCallsModelListAndMapsRejection() = runBlocking {
        val good = Recorder(200, """{"models":[]}""")
        provider(good).validateKey("KEY123")
        assertEquals("GET", good.request!!.method)
        assertTrue(good.request!!.url.toString().startsWith("https://example.invalid/v1beta/models"))
        assertEquals("KEY123", good.request!!.header("x-goog-api-key"))
        try {
            provider(Recorder(400, """{"error":{"message":"API key not valid"}}""")).validateKey("bad"); fail()
        } catch (_: EditException.InvalidKey) { }
    }

    @Test
    fun cancellingTheCoroutineCancelsTheCall() = runBlocking {
        val rec = Recorder(200, ok, delayMs = 3000)
        val job = async { provider(rec).edit(request()) }
        delay(200)
        val t0 = System.currentTimeMillis()
        job.cancel()
        try { job.await(); fail("should be cancelled") } catch (_: CancellationException) { }
        assertTrue("cancellation returned promptly", System.currentTimeMillis() - t0 < 2000)
    }

    @Test
    fun providerMetadataIsSane() {
        val p = provider(Recorder(200, ""))
        assertEquals("gemini", p.id)
        assertTrue(p.models.any { it.id == p.defaultModel })
        assertEquals(p.models.size, p.models.map { it.id }.toSet().size)
        assertTrue(p.models.all { it.id.startsWith("gemini-") })
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try { block() } catch (e: Throwable) { if (e is T) return e else throw e }
        throw AssertionError("expected ${T::class.simpleName}")
    }
}
