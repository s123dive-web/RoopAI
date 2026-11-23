package com.suresh.roopai.smoke

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import com.suresh.roopai.ui.editor.TAG_CATEGORIES
import com.suresh.roopai.ui.editor.TAG_TOOLS
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suresh.roopai.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Launches the real app (real Hilt graph, Room, DataStore, navigation, Compose screens and the
 * pure-Kotlin pipeline) on Robolectric. There is no key and no network here, which is exactly
 * the "works with no API key and no network" requirement. MediaPipe's native library cannot
 * load on the JVM, so this also covers the "no face detected" path.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w480dp-h1000dp-xhdpi")
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun waitForText(text: String, timeout: Long = 20_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun category(title: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNodeWithTag(TAG_CATEGORIES).performScrollToNode(hasText(title))
        return compose.onNodeWithText(title)
    }

    private fun tool(title: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNodeWithTag(TAG_TOOLS).performScrollToNode(hasText(title))
        return compose.onNodeWithText(title)
    }

    @Test
    fun homeSettingsAndHistoryOpenWithoutKeyOrNetwork() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Style any face")
            compose.onNodeWithText("Gallery").assertIsDisplayed()
            compose.onNodeWithText("Camera").assertIsDisplayed()
            waitForText("No edits yet")
            shot("home")

            compose.onNodeWithContentDescription("Settings").performClick()
            waitForText("AI edits (optional)")
            compose.onNodeWithText("No key added. AI tools show an \"Add API key\" prompt.").assertIsDisplayed()
            compose.onNodeWithText("Nano Banana 2 Lite").assertIsDisplayed()
            shot("settings")
            compose.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()

            waitForText("Style any face")
            compose.onNodeWithContentDescription("History").performClick()
            waitForText("Nothing here yet")
        }
    }

    @Test
    fun sharedImageOpensTheEditorAndOfflineToolsWork() {
        val uri = Uri.parse("content://com.example.gallery/photo/1")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(testJpeg()) }
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)

        ActivityScenario.launch<MainActivity>(intent).use {
            // editor is up: all ten categories from the brief plus Background and Adjust
            waitForText("Skin")
            for (c in com.suresh.roopai.edit.Category.entries) {
                category(c.title)
                compose.onNodeWithText(c.title).assertIsDisplayed()
            }
            category("Skin").performClick()
            waitForText("Smooth")
            // no face in a synthetic picture: clear message, app keeps working
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText("No face detected", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // an offline tool end to end: Adjust -> Filter -> rendered
            category("Adjust").performClick()
            waitForText("Filter")
            compose.onNodeWithText("Filter").performClick()
            waitForText("Mono")
            compose.onNodeWithText("Sepia").performClick()
            shot("editor_filter")
            tool("Rotate").performClick()
            compose.onNodeWithContentDescription("Rotate right").performClick()
            compose.onNodeWithText("Done").performClick()
            tool("Crop").performClick()
            waitForText("1:1")
            compose.onNodeWithText("1:1").performClick()
            shot("editor_crop")
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Undo").performClick()
            compose.onNodeWithContentDescription("Redo").performClick()

            // export: full-resolution render + write to the gallery
            compose.onNodeWithContentDescription("Save to gallery").performClick()
            waitForText("Saved to Pictures/RoopAI")

            // cloud tools are badged and gated: without a key nothing is sent
            category("Looks").performClick()
            waitForText("Wedding")
            compose.onNodeWithText("Wedding").performClick()
            waitForText("Add API key")
            shot("editor_need_key")
            compose.onNodeWithText("Not now").performClick()

            category("AI Edit").performClick()
            waitForText("Describe one change")
            compose.onNodeWithText("Add an API key in Settings to use AI edits.").assertIsDisplayed()
            shot("editor_ai")

            compose.onNodeWithContentDescription("Back").performClick()
            // the edit shows up on Home as a recent edit
            waitForText("Recent edits")
            compose.waitUntil(20_000) { compose.onAllNodesWithText("See all").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /** Saves a screenshot when run with -Droopai.visual.out=<dir> (developer aid, off in CI). */
    private fun shot(name: String) {
        val dir = System.getProperty("roopai.visual.out") ?: return
        try {
            compose.waitForIdle()
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(dir).mkdirs()
            java.io.File(dir, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            println("screenshot $name failed: $e")
        }
    }

    private fun testJpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(90, 140, 200))
        canvas.drawCircle(160f, 120f, 70f, Paint().apply { color = Color.rgb(230, 190, 150) })
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
