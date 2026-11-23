package com.suresh.roopai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.suresh.roopai.data.ProjectStore
import com.suresh.roopai.data.settings.AppSettings
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.ui.camera.CameraScreen
import com.suresh.roopai.ui.editor.EditorScreen
import com.suresh.roopai.ui.editor.EditorViewModel
import com.suresh.roopai.ui.history.HistoryScreen
import com.suresh.roopai.ui.home.HomeScreen
import com.suresh.roopai.ui.home.LibraryViewModel
import com.suresh.roopai.ui.settings.SettingsScreen
import com.suresh.roopai.ui.theme.RoopTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var projects: ProjectStore

    /** Image handed over by another app (share sheet / "open with"); consumed once. */
    private val incoming = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming.value = sharedImage(intent)
        setContent {
            val appSettings by settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val shared by incoming.collectAsStateWithLifecycle()
            RoopTheme(appSettings.theme) {
                Surface(Modifier.fillMaxSize()) { RoopNavHost(projects, shared) { incoming.value = null } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedImage(intent)?.let { incoming.value = it }
    }

    /** Only content:// URIs are accepted, so another app cannot point us at arbitrary files. */
    private fun sharedImage(intent: Intent?): String? {
        intent ?: return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        return uri?.takeIf { it.scheme == "content" }?.toString()
    }
}

private object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{${EditorViewModel.ARG_PROJECT}}"
    fun editor(id: String) = "editor/$id"
}

@Composable
private fun RoopNavHost(projects: ProjectStore, sharedImage: String?, onSharedConsumed: () -> Unit) {
    val nav = rememberNavController()
    // one library ViewModel for the whole activity so Home shows import errors raised on Camera
    val library = hiltViewModel<LibraryViewModel>()

    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            onSharedConsumed()
            nav.popBackStack(Routes.HOME, inclusive = false)
            library.import(sharedImage)
        }
    }
    val openEditor: (String) -> Unit = { id -> nav.navigate(Routes.editor(id)) { launchSingleTop = true } }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = library,
                thumbFile = projects::thumb,
                onOpenProject = openEditor,
                onCamera = { nav.navigate(Routes.CAMERA) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CAMERA) {
            val vm = library
            val import by vm.importState.collectAsStateWithLifecycle()
            LaunchedEffect(import.openProject) {
                import.openProject?.let { id ->
                    vm.consumeNavigation()
                    nav.navigate(Routes.editor(id)) { popUpTo(Routes.HOME) }
                }
            }
            LaunchedEffect(import.error) {
                // errors are shown on Home, which owns the dialog
                if (import.error != null) nav.popBackStack()
            }
            CameraScreen(importing = import.importing, onCaptured = vm::import, onError = vm::importFailed, onBack = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(library, projects::thumb, onOpen = openEditor, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) { SettingsScreen(hiltViewModel(), onBack = { nav.popBackStack() }) }
        composable(Routes.EDITOR, arguments = listOf(navArgument(EditorViewModel.ARG_PROJECT) { type = NavType.StringType })) {
            EditorScreen(hiltViewModel(), onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
    }
}
