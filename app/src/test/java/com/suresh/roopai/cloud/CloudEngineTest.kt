package com.suresh.roopai.cloud

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.RenderRequest
import com.suresh.roopai.engine.cloud.CloudConfig
import com.suresh.roopai.engine.cloud.CloudEngine
import com.suresh.roopai.engine.cloud.ImageEditProvider
import com.suresh.roopai.engine.cloud.ImageEditRequest
import com.suresh.roopai.engine.cloud.ImageEditResult
import com.suresh.roopai.engine.cloud.ModelInfo
import com.suresh.roopai.engine.cloud.ProviderRegistry
import com.suresh.roopai.engine.cloud.RetryPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudEngineTest {
    private class Provider : ImageEditProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override val models = listOf(ModelInfo("default-model", "Default"))
        override val defaultModel = "default-model"
        override val keyHelp = ""
        val requests = ArrayList<ImageEditRequest>()
        var failures = ArrayDeque<EditException>()
        var reply: ByteArray = ByteArray(0)
        override suspend fun edit(request: ImageEditRequest): ImageEditResult {
            requests.add(request)
            failures.removeFirstOrNull()?.let { throw it }
            return ImageEditResult(reply, "image/png", "note")
        }
        override suspend fun validateKey(apiKey: String) { if (apiKey != "K") throw EditException.InvalidKey() }
    }

    private val provider = Provider()
    private var config = CloudConfig("fake", "", "K", consentGiven = true, uploadLongSide = 64)
    private val waits = ArrayList<Long>()
    private val engine = CloudEngine(ProviderRegistry(listOf(provider)), { config }, RetryPolicy(3, 10, 2.0) { waits.add(it) })

    private fun bitmap(w: Int = 200, h: Int = 100) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }

    private fun pngOf(b: Bitmap) = java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test
    fun refusesWithoutKeyOrConsentBeforeAnyNetworkCall() = runBlocking {
        config = config.copy(apiKey = null)
        try { engine.edit(bitmap(), "p"); fail() } catch (_: EditException.NoApiKey) { }
        config = config.copy(apiKey = "  ")
        try { engine.edit(bitmap(), "p"); fail() } catch (_: EditException.NoApiKey) { }
        config = config.copy(apiKey = "K", consentGiven = false)
        try { engine.edit(bitmap(), "p"); fail() } catch (_: EditException.ConsentRequired) { }
        assertTrue("nothing may leave the device", provider.requests.isEmpty())
    }

    @Test
    fun uploadsADownscaledJpegAndDecodesTheResult() = runBlocking {
        provider.reply = pngOf(bitmap(30, 20))
        val src = bitmap(200, 100)
        val out = engine.edit(src, "the prompt")
        val req = provider.requests.single()
        assertEquals("K", req.apiKey); assertEquals("default-model", req.model); assertEquals("the prompt", req.prompt)
        assertEquals("image/jpeg", req.mimeType)
        val uploaded = android.graphics.BitmapFactory.decodeByteArray(req.imageBytes, 0, req.imageBytes.size)
        assertEquals(64, uploaded.width); assertEquals(32, uploaded.height)
        assertEquals(30, out.bitmap.width); assertEquals(20, out.bitmap.height)
        assertEquals("fake", out.providerId); assertEquals("default-model", out.model); assertEquals("image/png", out.mimeType)
        assertFalse("the caller's bitmap is never recycled", src.isRecycled)
    }

    @Test
    fun configuredModelWinsOverTheDefault() = runBlocking {
        provider.reply = pngOf(bitmap(8, 8))
        config = config.copy(model = "custom-model")
        assertEquals("custom-model", engine.edit(bitmap(), "p").model)
        assertEquals("custom-model", provider.requests.single().model)
    }

    @Test
    fun transientErrorsAreRetriedThenSucceed() = runBlocking {
        provider.reply = pngOf(bitmap(8, 8))
        provider.failures = ArrayDeque(listOf(EditException.Server(503), EditException.QuotaExceeded()))
        engine.edit(bitmap(), "p")
        assertEquals(3, provider.requests.size)
        assertEquals(listOf(10L, 20L), waits)
    }

    @Test
    fun permanentErrorsAreNotRetried() = runBlocking {
        provider.failures = ArrayDeque(listOf(EditException.InvalidKey()))
        try { engine.edit(bitmap(), "p"); fail() } catch (_: EditException.InvalidKey) { }
        assertEquals(1, provider.requests.size)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun undecodableReplyIsABadResponse() = runBlocking {
        provider.reply = byteArrayOf(1, 2, 3)
        try { engine.edit(bitmap(), "p"); fail() } catch (_: EditException.BadResponse) { }
    }

    @Test
    fun engineInterfaceAppliesCloudOpsInOrder() = runBlocking {
        provider.reply = pngOf(bitmap(16, 16))
        assertFalse(engine.isOffline)
        assertTrue(engine.supports(EditOp.CloudEdit("a", "A", "p1")))
        assertFalse(engine.supports(EditOp.Geometry(1)))
        val out = engine.apply(bitmap(), listOf(EditOp.CloudEdit("a", "A", "p1"), EditOp.CloudEdit("b", "B", "p2")), RenderRequest("k"))
        assertEquals(listOf("p1", "p2"), provider.requests.map { it.prompt })
        assertEquals(16, out.width)
    }

    @Test
    fun validateKey() = runBlocking {
        engine.validateKey("fake", " K ")
        try { engine.validateKey("fake", "wrong"); fail() } catch (_: EditException.InvalidKey) { }
        try { engine.validateKey("fake", ""); fail() } catch (_: EditException.NoApiKey) { }
    }
}
