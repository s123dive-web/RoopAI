package com.suresh.roopai.ui.editor

import android.graphics.Bitmap
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.EditStack

/** A rendered image handed to the UI. [bitmap] is null only in JVM tests. */
class PreviewFrame(val bitmap: Bitmap?, val width: Int, val height: Int) {
    val aspect: Float get() = if (height == 0) 1f else width.toFloat() / height
}

/** Normalised face rectangle in the coordinates of the un-cropped base image. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class CloudStatus(val hasKey: Boolean, val consentGiven: Boolean, val providerName: String, val model: String)

class SharePayload(val uri: String, val mime: String)

/**
 * Everything the editor needs from the engines and storage, behind one seam so the
 * ViewModel's logic (undo/redo, gating, error states) is testable without Android.
 */
interface EditorGateway {
    class Opened(val stack: EditStack, val original: PreviewFrame)

    suspend fun open(projectId: String): Opened

    /** Faces of the image the current stack edits (the original or the last AI result). */
    suspend fun faces(projectId: String, stack: EditStack): List<FaceBox>

    /** Renders the stack at preview size. [showFullFrame] ignores the crop (crop tool open). */
    suspend fun preview(projectId: String, stack: EditStack, showFullFrame: Boolean): PreviewFrame

    /** Runs a generative edit on the current result and returns the op with its cached file. */
    suspend fun cloudEdit(projectId: String, stack: EditStack, presetId: String, title: String, prompt: String): EditOp.CloudEdit

    /** Renders at export size and saves to the gallery. Returns the content URI. */
    suspend fun saveToGallery(projectId: String, stack: EditStack): String

    suspend fun share(projectId: String, stack: EditStack): SharePayload

    suspend fun persist(projectId: String, stack: EditStack, thumbnail: PreviewFrame?)

    /** Copies a picked photo into the project as a background; returns its file name. */
    suspend fun importBackground(projectId: String, uri: String): String

    suspend fun cloudStatus(): CloudStatus
    suspend fun setConsent(given: Boolean)

    /** Called when the editor closes: drop caches and unreferenced files. */
    suspend fun close(projectId: String)

    val needsStoragePermission: Boolean
}
