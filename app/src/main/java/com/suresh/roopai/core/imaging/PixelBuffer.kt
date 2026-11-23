package com.suresh.roopai.core.imaging

/** ARGB_8888 pixels in a plain IntArray. Pure Kotlin so all image maths is JVM-testable. */
class PixelBuffer(val width: Int, val height: Int, val px: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0) { "empty buffer" }
        require(px.size == width * height) { "pixel array size mismatch" }
    }

    operator fun get(x: Int, y: Int): Int = px[y * width + x]

    operator fun set(x: Int, y: Int, c: Int) {
        px[y * width + x] = c
    }

    fun copy(): PixelBuffer = PixelBuffer(width, height, px.copyOf())

    companion object {
        fun filled(width: Int, height: Int, color: Int) =
            PixelBuffer(width, height, IntArray(width * height) { color })
    }
}

/** Integer rectangle, right/bottom exclusive. */
data class IRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val isEmpty get() = width <= 0 || height <= 0

    fun clip(w: Int, h: Int) = IRect(
        left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h),
    )

    fun inset(d: Int) = IRect(left + d, top + d, right - d, bottom - d)
}
