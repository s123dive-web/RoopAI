package com.suresh.roopai.cloud

import com.suresh.roopai.edit.EditException
import com.suresh.roopai.engine.cloud.GeminiProvider
import com.suresh.roopai.engine.cloud.ImageEditProvider
import com.suresh.roopai.engine.cloud.ImageEditRequest
import com.suresh.roopai.engine.cloud.ImageEditResult
import com.suresh.roopai.engine.cloud.ModelInfo
import com.suresh.roopai.engine.cloud.ProviderRegistry
import com.suresh.roopai.engine.cloud.RetryPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class RetryPolicyTest {
    private val waits = ArrayList<Long>()
    private fun policy(max: Int = 3) = RetryPolicy(maxAttempts = max, baseDelayMs = 100, factor = 2.0, sleep = { waits.add(it) })

    @Test
    fun succeedsFirstTimeWithoutWaiting() = runBlocking {
        assertEquals("ok", policy().run { "ok" })
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun retriesServerErrorsWithExponentialBackoff() = runBlocking {
        var n = 0
        val result = policy().run { attempt -> n = attempt; if (attempt < 3) throw EditException.Server(503) else "done" }
        assertEquals("done", result)
        assertEquals(3, n)
        assertEquals(listOf(100L, 200L), waits)
    }

    @Test
    fun givesUpAfterMaxAttemptsAndRethrowsTheLastError() = runBlocking {
        var n = 0
        try {
            policy(max = 3).run<String> { n++; throw EditException.QuotaExceeded() }
            fail()
        } catch (_: EditException.QuotaExceeded) { }
        assertEquals(3, n)
        assertEquals(2, waits.size)
    }

    @Test
    fun doesNotRetryPermanentFailures() = runBlocking {
        for (e in listOf(
            EditException.InvalidKey(), EditException.NoNetwork(), EditException.Blocked(), EditException.Timeout(),
            EditException.ModelNotFound("m"), EditException.BadResponse(), EditException.Server(400), EditException.NoApiKey(),
        )) {
            var n = 0
            try { policy().run<String> { n++; throw e }; fail() } catch (caught: EditException) { assertSame(e, caught) }
            assertEquals(e.javaClass.simpleName, 1, n)
        }
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun otherExceptionsPropagateUntouched() = runBlocking {
        var n = 0
        try { policy().run<String> { n++; throw IllegalStateException("bug") }; fail() } catch (_: IllegalStateException) { }
        assertEquals(1, n)
    }

    @Test
    fun registryFallsBackToTheDefaultProvider() {
        val a = Fake("a"); val b = Fake("b")
        val reg = ProviderRegistry(listOf(a, b))
        assertSame(a, reg.default)
        assertSame(b, reg.get("b"))
        assertSame(a, reg.get("missing"))
        assertSame(a, reg.get(null))
        assertSame(a, reg.get(""))
        assertEquals("gemini", GeminiProvider.ID)
    }

    private class Fake(override val id: String) : ImageEditProvider {
        override val displayName = id
        override val models = listOf(ModelInfo("m", "M"))
        override val defaultModel = "m"
        override val keyHelp = ""
        override suspend fun edit(request: ImageEditRequest) = ImageEditResult(ByteArray(0), "image/png")
        override suspend fun validateKey(apiKey: String) = Unit
    }
}
