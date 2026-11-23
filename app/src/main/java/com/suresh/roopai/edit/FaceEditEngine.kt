package com.suresh.roopai.edit

import android.graphics.Bitmap

/**
 * One interface, two implementations: `LocalEngine` (offline, on-device) and `CloudEngine`
 * (optional, bring-your-own-key). The renderer walks the edit stack and hands each run of
 * operations to the engine that supports them.
 */
interface FaceEditEngine {
    val id: String

    /** True when this engine never touches the network. */
    val isOffline: Boolean

    fun supports(op: EditOp): Boolean

    /**
     * Applies [ops] (all of which satisfy [supports]) to [source] and returns a new bitmap.
     * Never mutates or recycles [source]. Throws [EditException] for expected failures and
     * honours coroutine cancellation.
     */
    suspend fun apply(source: Bitmap, ops: List<EditOp>, request: RenderRequest): Bitmap
}

/**
 * @param analysisKey identifies the image content being edited so on-device analysis
 *   (landmarks, segmentation) can be cached between slider moves.
 * @param faceIndex which detected face the face tools apply to.
 * @param projectDir folder holding files referenced by ops (background photos, cloud results).
 */
data class RenderRequest(
    val analysisKey: String,
    val faceIndex: Int = 0,
    val projectDir: java.io.File? = null,
)

/** Expected, user-explainable failures. */
sealed class EditException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoApiKey : EditException("Add an API key in Settings to use AI edits.")
    class ConsentRequired : EditException("Allow photo upload before using AI edits.")
    class InvalidKey(detail: String = "") : EditException("The API key was rejected. Check it in Settings." + suffix(detail))
    class QuotaExceeded(detail: String = "") : EditException("Quota or rate limit reached for this API key. Try again later or switch model." + suffix(detail))
    class NoNetwork(cause: Throwable? = null) : EditException("No internet connection. AI edits need a network; all other tools work offline.", cause)
    class Timeout(cause: Throwable? = null) : EditException("The AI service took too long to answer. Try again.", cause)
    class Blocked(detail: String = "") : EditException("The AI service declined this edit." + suffix(detail))
    class ModelNotFound(model: String) : EditException("Model \"$model\" is not available for this key. Pick another model in Settings.")
    class Server(val code: Int, detail: String = "") : EditException("The AI service returned an error ($code)." + suffix(detail))
    class BadResponse(detail: String = "") : EditException("The AI service did not return an image." + suffix(detail))
    class OutOfMemory : EditException("Not enough memory for this photo. Try a lower output quality.")
    class Unreadable(detail: String = "") : EditException("This image could not be opened." + suffix(detail))

    private companion object {
        fun suffix(detail: String) = if (detail.isBlank()) "" else " ($detail)"
    }
}
