package com.suresh.roopai.data

import app.cash.turbine.test
import com.suresh.roopai.data.image.OutputFormat
import com.suresh.roopai.data.settings.AppSettings
import com.suresh.roopai.data.settings.OutputQuality
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.data.settings.ThemeMode
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack
import com.suresh.roopai.edit.Tool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {
    private var now = 1000L
    private val dao = FakeEditDao()
    private val files = FakeProjectFiles()
    private val history = HistoryRepository(dao, files) { now }

    private val secrets = FakeSecretStore()
    private val settings = SettingsRepository(FakeDataStore(), secrets)

    // ------------------------------------------------------------ history

    @Test
    fun createThenLoadGivesAnEmptyStack() = runBlocking {
        history.create("p1")
        assertTrue(history.exists("p1"))
        assertEquals(EditStack(), history.load("p1"))
        assertNull(history.load("missing"))
        assertFalse(history.exists("missing"))
    }

    @Test
    fun saveStoresStackOpCountAndTimestamp() = runBlocking {
        history.create("p1")
        now = 2000
        val stack = EditStack(faceIndex = 1).withAdjust(EditOp.Adjust(Tool.SKIN_SMOOTH, 0.5f)).withAdjust(EditOp.Adjust(Tool.FILTER, 1f, variant = "mono"))
        assertTrue(history.save("p1", stack))
        assertEquals(stack, history.load("p1"))
        val row = dao.get("p1")!!
        assertEquals(2, row.opCount); assertEquals(2000, row.updatedAt); assertEquals(1000, row.createdAt)
    }

    @Test
    fun savingADeletedProjectDoesNotResurrectIt() = runBlocking {
        history.create("p1")
        history.delete("p1")
        assertFalse(history.save("p1", EditStack(faceIndex = 3)))
        assertFalse(history.exists("p1"))
        assertEquals(listOf("p1"), files.deleted)
    }

    @Test
    fun listIsNewestFirstAndUpdatesLive() = runBlocking {
        history.edits.test {
            assertEquals(emptyList<String>(), awaitItem().map { it.id })
            now = 10; history.create("a")
            assertEquals(listOf("a"), awaitItem().map { it.id })
            now = 20; history.create("b")
            assertEquals(listOf("b", "a"), awaitItem().map { it.id })
            now = 30; history.save("a", EditStack(faceIndex = 1))
            assertEquals(listOf("a", "b"), awaitItem().map { it.id })
            history.delete("b")
            assertEquals(listOf("a"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pruneKeepsOnlyFilesTheSavedStackReferences() = runBlocking {
        history.create("p1")
        history.save("p1", EditStack().withCloud(EditOp.CloudEdit("x", "X", "p", resultFile = "cloud_1.jpg")))
        history.prune("p1")
        assertEquals(listOf("p1" to setOf("cloud_1.jpg")), files.pruned)
        history.prune("missing")
        assertEquals(1, files.pruned.size)
    }

    @Test
    fun markExportedAndClear() = runBlocking {
        history.create("p1"); history.create("p2")
        history.markExported("p1", "content://media/1")
        assertEquals("content://media/1", dao.get("p1")!!.exportedUri)
        history.clear()
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(1, files.deletedAll)
    }

    // ----------------------------------------------------------- settings

    @Test
    fun defaults() = runBlocking {
        assertEquals(AppSettings(), settings.current())
        assertEquals(ThemeMode.SYSTEM, settings.current().theme)
        assertFalse("consent is never on by default", settings.current().cloudConsent)
        assertFalse(settings.hasApiKey("gemini"))
    }

    @Test
    fun settersRoundTrip() = runBlocking {
        settings.setTheme(ThemeMode.DARK)
        settings.setQuality(OutputQuality.LOSSLESS)
        settings.setModel("  custom-model ")
        settings.setConsent(true)
        val s = settings.settings.first()
        assertEquals(ThemeMode.DARK, s.theme); assertEquals(OutputQuality.LOSSLESS, s.quality)
        assertEquals("custom-model", s.model); assertTrue(s.cloudConsent)
    }

    @Test
    fun changingProviderResetsTheModel() = runBlocking {
        settings.setModel("m1")
        settings.setProvider("other")
        assertEquals("other", settings.current().providerId)
        assertEquals("", settings.current().model)
    }

    @Test
    fun apiKeyIsStoredOnlyInTheSecretStorePerProvider() = runBlocking {
        settings.setApiKey("gemini", "  SECRET  ")
        assertEquals("SECRET", settings.apiKey("gemini"))
        assertTrue(settings.hasApiKey("gemini"))
        assertFalse(settings.hasApiKey("other"))
        assertEquals(mapOf("api_key_gemini" to "SECRET"), secrets.values)
        settings.setApiKey("gemini", null)
        assertFalse(settings.hasApiKey("gemini"))
        settings.setApiKey("gemini", "   ")
        assertNull(settings.apiKey("gemini"))
    }

    @Test
    fun cloudConfigSnapshot() = runBlocking {
        var cfg = settings.cloudConfig("gemini")
        assertEquals("gemini", cfg.providerId); assertNull(cfg.apiKey); assertFalse(cfg.consentGiven)
        assertEquals(OutputQuality.HIGH.uploadLongSide, cfg.uploadLongSide)
        settings.setApiKey("gemini", "K"); settings.setConsent(true); settings.setQuality(OutputQuality.STANDARD); settings.setModel("m")
        cfg = settings.cloudConfig("gemini")
        assertEquals("K", cfg.apiKey); assertTrue(cfg.consentGiven); assertEquals(1024, cfg.uploadLongSide); assertEquals("m", cfg.model)
    }

    @Test
    fun qualityMapsToOutputFormats() {
        assertEquals(OutputFormat.Jpeg(90), OutputQuality.STANDARD.format)
        assertEquals(OutputFormat.Jpeg(95), OutputQuality.HIGH.format)
        assertEquals(OutputFormat.Jpeg(100), OutputQuality.MAXIMUM.format)
        assertEquals(OutputFormat.Png, OutputQuality.LOSSLESS.format)
        assertEquals("image/png", OutputFormat.Png.mime); assertEquals("jpg", OutputFormat.Jpeg(90).extension)
    }
}
