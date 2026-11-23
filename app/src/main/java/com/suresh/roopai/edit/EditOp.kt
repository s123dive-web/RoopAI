package com.suresh.roopai.edit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One non-destructive edit operation. A project is the untouched original photo plus an ordered
 * list of these; every render starts from the original again.
 */
@Serializable
sealed interface EditOp {

    /** An offline tool with its slider value and optional colour / style variant. */
    @Serializable
    @SerialName("adjust")
    data class Adjust(
        val tool: Tool,
        val amount: Float,
        val color: Int? = null,
        val variant: String? = null,
    ) : EditOp {
        val isNeutral get() = amount == 0f
    }

    /**
     * Crop / rotate. Applied in this order: quarter turns, flips, straighten, crop.
     * The crop is normalised (0..1) against the image after rotation and straightening.
     */
    @Serializable
    @SerialName("geometry")
    data class Geometry(
        val quarterTurns: Int = 0,
        val flipH: Boolean = false,
        val flipV: Boolean = false,
        val straighten: Float = 0f,
        val cropL: Float = 0f,
        val cropT: Float = 0f,
        val cropR: Float = 1f,
        val cropB: Float = 1f,
    ) : EditOp {
        val hasCrop get() = cropL > 0f || cropT > 0f || cropR < 1f || cropB < 1f
        val isIdentity get() = quarterTurns % 4 == 0 && !flipH && !flipV && straighten == 0f && !hasCrop
        fun withoutCrop() = copy(cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f)
    }

    /**
     * A generative edit. [resultFile] is the cached output inside the project folder; later
     * renders start from it instead of calling the network again. Empty while pending.
     */
    @Serializable
    @SerialName("cloud")
    data class CloudEdit(
        val presetId: String,
        val title: String,
        val prompt: String,
        val provider: String = "",
        val model: String = "",
        val resultFile: String = "",
    ) : EditOp
}
