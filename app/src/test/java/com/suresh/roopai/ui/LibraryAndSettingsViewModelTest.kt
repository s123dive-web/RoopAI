package com.suresh.roopai.ui

import com.suresh.roopai.data.FakeDataStore
import com.suresh.roopai.data.FakeEditDao
import com.suresh.roopai.data.FakeProjectFiles
import com.suresh.roopai.data.FakeSecretStore
import com.suresh.roopai.data.HistoryRepository
import com.suresh.roopai.data.settings.OutputQuality
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.data.settings.ThemeMode
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.engine.cloud.CloudEngine
import com.suresh.roopai.engine.cloud.ImageEditProvider
import com.suresh.roopai.engine.cloud.ImageEditRequest
import com.suresh.roopai.engine.cloud.ImageEditResult
import com.suresh.roopai.engine.cloud.ModelInfo
import com.suresh.roopai.engine.cloud.ProviderRegistry
import com.suresh.roopai.ui.home.LibraryViewModel
import com.suresh.roopai.ui.settings.KeyCheck
import com.suresh.roopai.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAndSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val dao = FakeEditDao()
    private val files = FakeProjectFiles()
    private val history = HistoryRepository(dao, files) { 5L }
    private val secrets = FakeSecretStore()
    private val repo = SettingsRepository(FakeDataStore(), secrets)

    private class FakeProvider : ImageEditProvider {
        override val id = "fake"
        override val displayName = "Fake AI"
        override val models = listOf(ModelInfo("fast", "Fast"), ModelInfo("best", "Best"))
        override val defaultModel = "fast"
        override val keyHelp = "help"
        var validKey = "GOOD"
        var validated = 0
        override suspend fun edit(request: ImageEditRequest) = ImageEditResult(ByteArray(1), "image/png")
        override suspend fun validateKey(apiKey: String) { validated++; if (apiKey != validKey) throw EditException.InvalidKey() }
    }

    private val provider = FakeProvider()
    private val registry = ProviderRegistry(listOf(provider))
    private val cloud = CloudEngine(registry, config = { repo.cloudConfig("fake") })

    // ------------------------------------------------------------ library

    @Test
    fun importCreatesAProjectAndNavigatesOnce() = runTest {
        val vm = LibraryViewModel(history) { uri -> "proj-" + uri.takeLast(1) }
        vm.import("content://photo/7")
        assertTrue(vm.importState.value.importing)
        advanceUntilIdle()
        assertEquals("proj-7", vm.importState.value.openProject)
        assertFalse(vm.importState.value.importing)
        assertTrue(history.exists("proj-7"))
        vm.consumeNavigation()
        assertNull(vm.importState.value.openProject)
    }

    @Test
    fun importFailureShowsTheReasonAndCreatesNothing() = runTest {
        val vm = LibraryViewModel(history) { throw EditException.Unreadable("unsupported or damaged file") }
        vm.import("content://bad"); advanceUntilIdle()
        assertTrue(vm.importState.value.error!!.contains("could not be opened"))
        assertTrue(dao.rows.value.isEmpty())
        vm.dismissError()
        assertNull(vm.importState.value.error)

        val crash = LibraryViewModel(history) { throw IllegalStateException("boom") }
        crash.import("x"); advanceUntilIdle()
        assertEquals("This image could not be opened.", crash.importState.value.error)
    }

    @Test
    fun deleteAndClearRemoveRowsAndFiles() = runTest {
        history.create("a"); history.create("b")
        val vm = LibraryViewModel(history) { "x" }
        vm.delete("a"); advanceUntilIdle()
        assertEquals(listOf("b"), history.edits.first().map { it.id })
        assertEquals(listOf("a"), files.deleted)
        vm.clearAll(); advanceUntilIdle()
        assertTrue(dao.rows.value.isEmpty()); assertEquals(1, files.deletedAll)
    }

    // ----------------------------------------------------------- settings

    private fun settingsVm() = SettingsViewModel(repo, registry, cloud, history)

    @Test
    fun initialStateHasNoKeyAndUsesProviderDefaults() = runTest {
        val vm = settingsVm(); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.loaded); assertFalse(s.hasKey); assertEquals("", s.keyHint)
        assertEquals("fake", s.provider!!.id); assertEquals("fast", s.model)
        assertFalse(s.settings.cloudConsent)
    }

    @Test
    fun savingAKeyStoresItEncryptedSideAndShowsOnlyAHint() = runTest {
        val vm = settingsVm(); advanceUntilIdle()
        vm.saveKey("   "); advanceUntilIdle()
        assertFalse(vm.state.value.hasKey)
        vm.consumeMessage()
        vm.saveKey("  SECRET-KEY-1234 "); advanceUntilIdle()
        assertTrue(vm.state.value.hasKey)
        assertEquals("••••1234", vm.state.value.keyHint)
        assertFalse("the UI state never holds the key", vm.state.value.toString().contains("SECRET-KEY"))
        assertEquals("SECRET-KEY-1234", secrets.values["api_key_fake"])
        vm.removeKey(); advanceUntilIdle()
        assertFalse(vm.state.value.hasKey); assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun testKeyReportsValidInvalidAndMissing() = runTest {
        val vm = settingsVm(); advanceUntilIdle()
        vm.testKey(); advanceUntilIdle()
        assertTrue((vm.state.value.keyCheck as KeyCheck.Failed).message.contains("Add an API key"))
        assertEquals(0, provider.validated)
        vm.saveKey("BAD"); advanceUntilIdle()
        vm.testKey(); advanceUntilIdle()
        assertTrue((vm.state.value.keyCheck as KeyCheck.Failed).message.contains("rejected"))
        vm.saveKey("GOOD"); advanceUntilIdle()
        assertEquals(KeyCheck.Idle, vm.state.value.keyCheck)
        vm.testKey(); advanceUntilIdle()
        assertEquals(KeyCheck.Valid, vm.state.value.keyCheck)
    }

    @Test
    fun preferencesFlowBackIntoState() = runTest {
        val vm = settingsVm(); advanceUntilIdle()
        vm.setTheme(ThemeMode.DARK); vm.setQuality(OutputQuality.MAXIMUM); vm.setModel("best"); vm.setConsent(true)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(ThemeMode.DARK, s.settings.theme); assertEquals(OutputQuality.MAXIMUM, s.settings.quality)
        assertEquals("best", s.model); assertTrue(s.settings.cloudConsent)
        vm.setModel("   "); advanceUntilIdle()
        assertEquals("blank model is ignored", "best", vm.state.value.model)
        vm.setModel("brand-new-model"); advanceUntilIdle()
        assertEquals("brand-new-model", vm.state.value.model)
    }

    @Test
    fun clearHistoryWipesEverything() = runTest {
        history.create("a")
        val vm = settingsVm(); advanceUntilIdle()
        vm.clearHistory(); advanceUntilIdle()
        assertTrue(dao.rows.value.isEmpty())
        assertEquals("History cleared.", vm.state.value.message)
    }
}
