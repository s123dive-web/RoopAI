package com.suresh.roopai.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rectangle where an image of [aspect] sits when fitted inside [container]. */
fun fitRect(container: Size, aspect: Float): Rect {
    if (container.width <= 0f || container.height <= 0f || aspect <= 0f) return Rect.Zero
    val cAspect = container.width / container.height
    return if (aspect > cAspect) {
        val h = container.width / aspect
        Rect(0f, (container.height - h) / 2, container.width, (container.height + h) / 2)
    } else {
        val w = container.height * aspect
        Rect((container.width - w) / 2, 0f, (container.width + w) / 2, container.height)
    }
}

private enum class Handle { NONE, MOVE, TL, TR, BL, BR }

/**
 * Applies a drag to a normalised crop rectangle [c] = (l, t, r, b). [aspectNorm] is the locked
 * aspect in normalised units (width / height), or null for a free crop.
 */
internal fun dragCrop(c: FloatArray, handle: Int, dx: Float, dy: Float, aspectNorm: Float?, minSize: Float = 0.08f): FloatArray {
    var l = c[0]; var t = c[1]; var r = c[2]; var b = c[3]
    when (handle) {
        1 -> { // move
            val w = r - l; val h = b - t
            l = (l + dx).coerceIn(0f, 1f - w); t = (t + dy).coerceIn(0f, 1f - h)
            r = l + w; b = t + h
        }
        else -> {
            val left = handle == 2 || handle == 4
            val top = handle == 2 || handle == 3
            if (left) l = (l + dx).coerceIn(0f, r - minSize) else r = (r + dx).coerceIn(l + minSize, 1f)
            if (top) t = (t + dy).coerceIn(0f, b - minSize) else b = (b + dy).coerceIn(t + minSize, 1f)
            if (aspectNorm != null) {
                // keep the ratio: derive the height from the width, anchored at the opposite corner
                var w = r - l
                var h = w / aspectNorm
                val maxH = if (top) b else 1f - t
                if (h > maxH) { h = maxH; w = h * aspectNorm }
                if (left) l = r - w else r = l + w
                if (top) t = b - h else b = t + h
            }
        }
    }
    return floatArrayOf(l, t, r, b)
}

/**
 * Shows the rendered frame with pinch zoom, the face picker overlay and the crop overlay.
 * [crop] non-null switches to crop mode (zoom disabled, handles shown).
 */
@Composable
fun PreviewCanvas(
    frame: PreviewFrame?,
    faces: List<FaceBox>,
    selectedFace: Int,
    showFaces: Boolean,
    crop: FloatArray?,
    cropAspect: Float?,
    onPickFace: (Int) -> Unit,
    onCropChanged: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var liveCrop by remember(crop?.toList()) { mutableStateOf(crop) }
    val cropMode = crop != null
    val handlePx = with(LocalDensity.current) { 36.dp.toPx() }
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }

    val facesState by rememberUpdatedState(faces)
    val showFacesState by rememberUpdatedState(showFaces)
    val frameState by rememberUpdatedState(frame)
    val pick by rememberUpdatedState(onPickFace)
    val cropDone by rememberUpdatedState(onCropChanged)
    val aspectState by rememberUpdatedState(cropAspect)

    if (cropMode && (scale != 1f || pan != Offset.Zero)) { scale = 1f; pan = Offset.Zero }

    var gestures = modifier.fillMaxSize().clipToBounds()
    gestures = if (cropMode) {
        gestures.pointerInput(Unit) {
            var handle = Handle.NONE
            detectDragGestures(
                onDragStart = { pos ->
                    val f = frameState; val c = liveCrop
                    handle = Handle.NONE
                    if (f != null && c != null) {
                        val rect = fitRect(Size(size.width.toFloat(), size.height.toFloat()), f.aspect)
                        val l = rect.left + c[0] * rect.width; val t = rect.top + c[1] * rect.height
                        val r = rect.left + c[2] * rect.width; val b = rect.top + c[3] * rect.height
                        fun near(x: Float, y: Float) = abs(pos.x - x) < handlePx && abs(pos.y - y) < handlePx
                        handle = when {
                            near(l, t) -> Handle.TL
                            near(r, t) -> Handle.TR
                            near(l, b) -> Handle.BL
                            near(r, b) -> Handle.BR
                            pos.x in l..r && pos.y in t..b -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    }
                },
                onDrag = { change, drag ->
                    val f = frameState; val c = liveCrop
                    if (f != null && c != null && handle != Handle.NONE) {
                        change.consume()
                        val rect = fitRect(Size(size.width.toFloat(), size.height.toFloat()), f.aspect)
                        if (rect.width > 0f && rect.height > 0f) {
                            // locked ratio is given in pixels; convert to normalised units of this frame
                            val norm = aspectState?.let { it / f.aspect }
                            liveCrop = dragCrop(c, handle.ordinal, drag.x / rect.width, drag.y / rect.height, norm)
                        }
                    }
                },
                onDragEnd = { liveCrop?.let { if (handle != Handle.NONE) cropDone(it) } },
            )
        }
    } else {
        gestures
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    // keep the pinch centre fixed while zooming
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val focus = centroid - center
                    var p = (pan - focus) * (newScale / scale) + focus + panChange
                    val maxX = size.width * (newScale - 1f) / 2f; val maxY = size.height * (newScale - 1f) / 2f
                    p = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                    scale = newScale; pan = if (newScale == 1f) Offset.Zero else p
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { scale = 1f; pan = Offset.Zero },
                    onTap = { pos ->
                        val f = frameState
                        if (f != null && showFacesState && facesState.size > 1) {
                            val rect = fitRect(Size(size.width.toFloat(), size.height.toFloat()), f.aspect)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val p = (pos - center - pan) / scale + center
                            val u = (p.x - rect.left) / rect.width; val v = (p.y - rect.top) / rect.height
                            val hit = facesState.indexOfFirst { u in it.left..it.right && v in it.top..it.bottom }
                            if (hit >= 0) pick(hit)
                        }
                    },
                )
            }
    }

    Canvas(gestures) {
        val f = frame ?: return@Canvas
        val bmp = f.bitmap ?: return@Canvas
        if (bmp.isRecycled) return@Canvas
        val base = fitRect(size, f.aspect)
        val center = Offset(size.width / 2f, size.height / 2f)
        fun tx(p: Offset) = (p - center) * scale + center + pan
        val tl = tx(base.topLeft); val br = tx(base.bottomRight)
        val rect = Rect(tl, br)
        drawImage(
            bmp.asImageBitmap(),
            srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(max(1, rect.width.roundToInt()), max(1, rect.height.roundToInt())),
            filterQuality = FilterQuality.Medium,
        )
        if (showFaces && faces.size > 1 && !cropMode) {
            faces.forEachIndexed { i, face ->
                val selected = i == selectedFace
                drawRoundRect(
                    color = if (selected) Color(0xFFFFD27A) else Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(rect.left + face.left * rect.width, rect.top + face.top * rect.height),
                    size = Size((face.right - face.left) * rect.width, (face.bottom - face.top) * rect.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 6),
                    style = Stroke(if (selected) stroke * 1.6f else stroke),
                )
            }
        }
        val c = liveCrop
        if (cropMode && c != null) {
            val l = rect.left + c[0] * rect.width; val t = rect.top + c[1] * rect.height
            val r = rect.left + c[2] * rect.width; val b = rect.top + c[3] * rect.height
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(rect.left, rect.top), Size(rect.width, max(0f, t - rect.top)))
            drawRect(dim, Offset(rect.left, b), Size(rect.width, max(0f, rect.bottom - b)))
            drawRect(dim, Offset(rect.left, t), Size(max(0f, l - rect.left), b - t))
            drawRect(dim, Offset(r, t), Size(max(0f, rect.right - r), b - t))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(stroke))
            for (k in 1..2) {
                val x = l + (r - l) * k / 3f; val y = t + (b - t) * k / 3f
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, t), Offset(x, b), stroke / 2)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(l, y), Offset(r, y), stroke / 2)
            }
            val len = min(handlePx * 0.6f, min(r - l, b - t) / 2)
            val w = stroke * 2.5f
            for ((x, y, sx, sy) in listOf(listOf(l, t, 1f, 1f), listOf(r, t, -1f, 1f), listOf(l, b, 1f, -1f), listOf(r, b, -1f, -1f))) {
                drawLine(Color.White, Offset(x, y), Offset(x + len * sx, y), w)
                drawLine(Color.White, Offset(x, y), Offset(x, y + len * sy), w)
            }
        }
    }
}
