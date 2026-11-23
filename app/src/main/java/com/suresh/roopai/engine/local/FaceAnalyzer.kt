package com.suresh.roopai.engine.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.suresh.roopai.core.imaging.LowResMask
import com.suresh.roopai.data.image.BitmapIO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** Source of landmarks and segmentation masks for an image. */
interface FaceAnalysisSource {
    /**
     * @param key identifies the image content; results are cached per key.
     * @param needSegmentation also compute hair / background masks (slower).
     */
    suspend fun analyze(key: String, image: Bitmap, needSegmentation: Boolean): FaceAnalysis
}

/**
 * MediaPipe Face Landmarker + multiclass selfie segmenter. Both models are bundled in the APK
 * assets and run on CPU; nothing is downloaded and no network is used.
 */
@Singleton
class FaceAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : FaceAnalysisSource {
    private val mutex = Mutex()
    private var landmarker: FaceLandmarker? = null
    private var segmenter: ImageSegmenter? = null
    private var landmarkerFailed = false
    private var segmenterFailed = false

    private class Entry(val faces: List<FaceLandmarks>, var hair: LowResMask? = null, var background: LowResMask? = null, var segDone: Boolean = false)

    private val cache = object : LinkedHashMap<String, Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 6
    }

    override suspend fun analyze(key: String, image: Bitmap, needSegmentation: Boolean): FaceAnalysis =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                var entry = cache[key]
                if (entry == null) {
                    entry = Entry(detectFaces(image))
                    cache[key] = entry
                }
                if (needSegmentation && !entry.segDone) {
                    segment(image)?.let { (hair, bg) -> entry.hair = hair; entry.background = bg }
                    entry.segDone = true
                }
                FaceAnalysis(entry.faces, entry.hair, entry.background)
            }
        }

    private fun detectFaces(image: Bitmap): List<FaceLandmarks> {
        val lm = landmarker ?: createLandmarker() ?: return emptyList()
        val scaled = BitmapIO.scaleToFit(image, 1280)
        return try {
            val result = lm.detect(BitmapImageBuilder(scaled).build())
            result.faceLandmarks().mapNotNull { pts ->
                if (pts.size < FaceMesh.POINTS) return@mapNotNull null
                val arr = FloatArray(FaceMesh.POINTS * 2)
                for (i in 0 until FaceMesh.POINTS) { arr[2 * i] = pts[i].x(); arr[2 * i + 1] = pts[i].y() }
                FaceLandmarks(arr)
            }.sortedBy { it.bounds[0] } // left to right, stable numbering for the face picker
        } catch (e: Exception) {
            Log.w(TAG, "face detection failed", e)
            emptyList()
        } finally {
            if (scaled !== image) scaled.recycle()
        }
    }

    private fun segment(image: Bitmap): Pair<LowResMask, LowResMask>? {
        val seg = segmenter ?: createSegmenter() ?: return null
        val scaled = BitmapIO.scaleToFit(image, 512)
        return try {
            val result = seg.segment(BitmapImageBuilder(scaled).build())
            val masks = result.confidenceMasks().orElse(null) ?: return null
            if (masks.size <= HAIR) return null
            val bg = toMask(masks[BACKGROUND])
            val hair = toMask(masks[HAIR])
            if (bg == null || hair == null) null else hair to bg
        } catch (e: Exception) {
            Log.w(TAG, "segmentation failed", e)
            null
        } finally {
            if (scaled !== image) scaled.recycle()
        }
    }

    private fun toMask(img: MPImage): LowResMask? {
        val buf = ByteBufferExtractor.extract(img).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val w = img.width; val h = img.height
        if (buf.remaining() < w * h) return null
        val data = FloatArray(w * h)
        buf.get(data)
        return LowResMask(w, h, data)
    }

    private fun createLandmarker(): FaceLandmarker? {
        if (landmarkerFailed) return null
        return try {
            FaceLandmarker.createFromOptions(
                context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(LANDMARK_MODEL).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(MAX_FACES)
                    .setMinFaceDetectionConfidence(0.4f)
                    .build(),
            ).also { landmarker = it }
        } catch (e: Throwable) {
            Log.e(TAG, "face landmarker unavailable", e)
            landmarkerFailed = true
            null
        }
    }

    private fun createSegmenter(): ImageSegmenter? {
        if (segmenterFailed) return null
        return try {
            ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(SEGMENT_MODEL).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true)
                    .setOutputCategoryMask(false)
                    .build(),
            ).also { segmenter = it }
        } catch (e: Throwable) {
            Log.e(TAG, "segmenter unavailable", e)
            segmenterFailed = true
            null
        }
    }

    companion object {
        private const val TAG = "FaceAnalyzer"
        const val LANDMARK_MODEL = "face_landmarker.task"
        const val SEGMENT_MODEL = "selfie_multiclass_256x256.tflite"
        const val MAX_FACES = 6

        // class indices of selfie_multiclass_256x256
        private const val BACKGROUND = 0
        private const val HAIR = 1
    }
}
