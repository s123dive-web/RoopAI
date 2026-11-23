package com.suresh.roopai.engine.local

import com.suresh.roopai.core.imaging.LowResMask
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Landmark index groups of the 478-point MediaPipe face mesh (canonical topology).
 * "R" is the subject's right side, which is on the left of an unmirrored photo.
 */
object FaceMesh {
    const val POINTS = 478

    val FACE_OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    )
    val LIPS_OUTER = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)
    val LIPS_INNER = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)

    val EYE_R = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
    val EYE_L = intArrayOf(263, 466, 388, 387, 386, 385, 384, 398, 362, 382, 381, 380, 374, 373, 390, 249)

    /** Upper lid, outer corner -> inner corner. */
    val LID_UP_R = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133)
    val LID_UP_L = intArrayOf(263, 466, 388, 387, 386, 385, 384, 398, 362)

    /** Lower lid, outer corner -> inner corner. */
    val LID_LOW_R = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
    val LID_LOW_L = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362)

    /** Below-the-eye ring, inner -> outer; closes the under-eye polygon with the lower lid. */
    val UNDER_EYE_R = intArrayOf(121, 120, 119, 118, 117, 111)
    val UNDER_EYE_L = intArrayOf(350, 349, 348, 347, 346, 340)

    val BROW_R = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)
    val BROW_L = intArrayOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)

    val FOREHEAD = intArrayOf(10, 338, 297, 332, 284, 300, 293, 334, 296, 336, 9, 107, 66, 105, 63, 70, 54, 103, 67, 109)

    const val IRIS_R = 468
    const val IRIS_L = 473
    const val EYE_OUT_R = 33
    const val EYE_OUT_L = 263
    const val EYE_IN_R = 133
    const val EYE_IN_L = 362
    const val FOREHEAD_TOP = 10
    const val FOREHEAD_MID = 151
    const val GLABELLA = 9
    const val NOSE_TIP = 1
    const val CHIN = 152
    const val SIDE_R = 234
    const val SIDE_L = 454
    const val TEMPLE_R = 127
    const val TEMPLE_L = 356
    const val CHEEK_R = 205
    const val CHEEK_L = 425
    const val CHEEKBONE_R = 50
    const val CHEEKBONE_L = 280
    const val ALAR_R = 129
    const val ALAR_L = 358
    const val NOSTRIL_R = 64
    const val NOSTRIL_L = 294
    const val NOSE_WING_R = 49
    const val NOSE_WING_L = 279
    const val MOUTH_R = 61
    const val MOUTH_L = 291
    const val JAW_R = 172
    const val JAW_L = 397
    const val JAW_LOW_R = 150
    const val JAW_LOW_L = 379
    const val EAR_LOW_R = 132
    const val EAR_LOW_L = 361
}

/** One detected face: landmarks normalised to 0..1 of the analysed image. */
class FaceLandmarks(val norm: FloatArray) {
    init { require(norm.size >= FaceMesh.POINTS * 2) { "expected ${FaceMesh.POINTS} landmarks" } }

    /** Normalised bounding box: left, top, right, bottom. */
    val bounds: FloatArray by lazy {
        var l = 1f; var t = 1f; var r = 0f; var b = 0f
        for (i in 0 until FaceMesh.POINTS) {
            l = min(l, norm[2 * i]); r = max(r, norm[2 * i]); t = min(t, norm[2 * i + 1]); b = max(b, norm[2 * i + 1])
        }
        floatArrayOf(l, t, r, b)
    }

    fun scaled(width: Int, height: Int): FaceGeom = FaceGeom(this, width, height)
}

/** Landmarks in pixels for a concrete render size plus the face's own axes and scale. */
class FaceGeom(private val lm: FaceLandmarks, val width: Int, val height: Int) {
    fun x(i: Int) = lm.norm[2 * i] * width
    fun y(i: Int) = lm.norm[2 * i + 1] * height

    /** Face width in pixels (cheek to cheek); every radius in the pipeline is a fraction of it. */
    val fw: Float = max(4f, dist(FaceMesh.SIDE_R, FaceMesh.SIDE_L))

    /** Unit vector from the subject's right to left (image left -> right). */
    val ux: Float
    val uy: Float

    /** Unit vector pointing from forehead to chin. */
    val vx: Float
    val vy: Float

    init {
        val dx = x(FaceMesh.EYE_OUT_L) - x(FaceMesh.EYE_OUT_R)
        val dy = y(FaceMesh.EYE_OUT_L) - y(FaceMesh.EYE_OUT_R)
        val len = max(1e-3f, sqrt(dx * dx + dy * dy))
        ux = dx / len; uy = dy / len
        // perpendicular, flipped so it points towards the chin
        var px = -uy; var py = ux
        val cx = x(FaceMesh.CHIN) - x(FaceMesh.FOREHEAD_TOP)
        val cy = y(FaceMesh.CHIN) - y(FaceMesh.FOREHEAD_TOP)
        if (px * cx + py * cy < 0f) { px = -px; py = -py }
        vx = px; vy = py
    }

    /** Roll of the face in radians (0 = upright). */
    val roll: Float get() = kotlin.math.atan2(uy, ux)

    /**
     * Rough yaw indicator in -1..1: negative when the subject's right side (image left) is
     * turned away from the camera, positive when the left side is.
     */
    val yaw: Float
        get() {
            val nx = x(FaceMesh.NOSE_TIP); val ny = y(FaceMesh.NOSE_TIP)
            val dr = (nx - x(FaceMesh.SIDE_R)) * ux + (ny - y(FaceMesh.SIDE_R)) * uy
            val dl = (x(FaceMesh.SIDE_L) - nx) * ux + (y(FaceMesh.SIDE_L) - ny) * uy
            val sum = max(1e-3f, dr + dl)
            return ((dl - dr) / sum).coerceIn(-1f, 1f) * -1f
        }

    fun dist(a: Int, b: Int): Float {
        val dx = x(a) - x(b); val dy = y(a) - y(b)
        return sqrt(dx * dx + dy * dy)
    }

    fun poly(idx: IntArray): FloatArray {
        val out = FloatArray(idx.size * 2)
        for (k in idx.indices) { out[2 * k] = x(idx[k]); out[2 * k + 1] = y(idx[k]) }
        return out
    }

    fun midX(a: Int, b: Int) = (x(a) + x(b)) / 2f
    fun midY(a: Int, b: Int) = (y(a) + y(b)) / 2f
}

/** Output of the on-device models for one image. */
class FaceAnalysis(
    val faces: List<FaceLandmarks>,
    /** Hair confidence 0..1, or null when segmentation is unavailable. */
    val hair: LowResMask?,
    /** Background confidence 0..1, or null when segmentation is unavailable. */
    val background: LowResMask?,
) {
    fun face(index: Int): FaceLandmarks? = faces.getOrNull(index) ?: faces.firstOrNull()

    companion object {
        val EMPTY = FaceAnalysis(emptyList(), null, null)
    }
}
