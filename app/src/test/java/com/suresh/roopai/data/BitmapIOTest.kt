package com.suresh.roopai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suresh.roopai.data.image.AtomicFiles
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.data.image.OutputFormat
import com.suresh.roopai.edit.EditException
import com.suresh.roopai.edit.EditOp
import com.suresh.roopai.edit.RenderRequest
import com.suresh.roopai.edit.Tool
import com.suresh.roopai.engine.local.FaceAnalysis
import com.suresh.roopai.engine.local.FaceAnalysisSource
import com.suresh.roopai.engine.local.LocalEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** Bitmap decode / encode on Robolectric's native graphics (real Skia codecs). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapIOTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Left half red, right half blue, so orientation is observable. */
    private fun jpeg(name: String, w: Int, h: Int, orientation: Int? = null): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) bmp.setPixel(x, y, if (x < w / 2) Color.RED else Color.BLUE)
        val file = File(context.cacheDir, name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (orientation != null) ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return file
    }

    private fun isRed(c: Int) = Color.red(c) > 180 && Color.blue(c) < 90
    private fun isBlue(c: Int) = Color.blue(c) > 180 && Color.red(c) < 90

    @Test
    fun infoReportsSizeAndUprightSize() {
        val plain = BitmapIO.info(jpeg("a.jpg", 200, 100))
        assertEquals(200, plain.width); assertEquals(100, plain.height); assertEquals(200, plain.uprightWidth)
        val rotated = BitmapIO.info(jpeg("b.jpg", 200, 100, ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(200, rotated.width); assertEquals(100, rotated.uprightWidth); assertEquals(200, rotated.uprightHeight)
    }

    @Test
    fun exifRotationIsAppliedOnDecode() {
        // stored landscape with "rotate 90": upright image is portrait, red half on top
        val bmp = BitmapIO.decode(jpeg("c.jpg", 200, 100, ExifInterface.ORIENTATION_ROTATE_90), 4000)
        assertEquals(100, bmp.width); assertEquals(200, bmp.height)
        assertTrue(isRed(bmp.getPixel(50, 20))); assertTrue(isBlue(bmp.getPixel(50, 180)))
        val flipped = BitmapIO.decode(jpeg("d.jpg", 200, 100, ExifInterface.ORIENTATION_FLIP_HORIZONTAL), 4000)
        assertTrue(isBlue(flipped.getPixel(20, 50))); assertTrue(isRed(flipped.getPixel(180, 50)))
        val upside = BitmapIO.decode(jpeg("e.jpg", 200, 100, ExifInterface.ORIENTATION_ROTATE_180), 4000)
        assertEquals(200, upside.width); assertTrue(isBlue(upside.getPixel(20, 50)))
    }

    @Test
    fun largeImagesAreDownsampledToTheRequestedBudget() {
        val file = jpeg("big.jpg", 2400, 1800)
        val preview = BitmapIO.decode(file, 600)
        assertEquals(600, preview.width); assertEquals(450, preview.height)
        assertEquals(Bitmap.Config.ARGB_8888, preview.config)
        val budget = BitmapIO.decode(file, Int.MAX_VALUE, maxPixels = 1_000_000)
        assertTrue(budget.width.toLong() * budget.height <= 1_000_000)
        assertEquals(4.0 / 3, budget.width.toDouble() / budget.height, 0.01)
        val full = BitmapIO.decode(file, Int.MAX_VALUE)
        assertEquals(2400, full.width)
        // rotated + downsampled keeps the upright aspect ratio
        val rot = BitmapIO.decode(jpeg("bigrot.jpg", 2400, 1800, ExifInterface.ORIENTATION_ROTATE_270), 600)
        assertEquals(450, rot.width); assertEquals(600, rot.height)
    }

    @Test
    fun unreadableFilesGiveClearErrors() {
        val junk = File(context.cacheDir, "junk.jpg").apply { writeText("this is not an image") }
        try { BitmapIO.info(junk); fail() } catch (_: EditException.Unreadable) { }
        try { BitmapIO.decode(junk, 100); fail() } catch (_: EditException.Unreadable) { }
        try { BitmapIO.info(File(context.cacheDir, "missing.jpg")); fail() } catch (_: EditException.Unreadable) { }
        try { BitmapIO.decodeBytes(byteArrayOf(1, 2, 3)); fail() } catch (_: EditException.BadResponse) { }
    }

    @Test
    fun pixelRoundTripAndScaling() {
        val bmp = BitmapIO.decode(jpeg("f.jpg", 64, 32), 4000)
        val px = BitmapIO.toPixels(bmp)
        assertEquals(64, px.width); assertEquals(32 * 64, px.px.size)
        val back = BitmapIO.toBitmap(px)
        assertEquals(bmp.getPixel(10, 10), back.getPixel(10, 10))
        assertSame(bmp, BitmapIO.scaleToFit(bmp, 100))
        val small = BitmapIO.scaleToFit(bmp, 16)
        assertNotSame(bmp, small); assertEquals(16, small.width); assertEquals(8, small.height)
        val crop = BitmapIO.centerCropPixels(bmp, 10, 10)
        assertEquals(100, crop.size)
        assertTrue(isRed(crop[5 * 10 + 1])); assertTrue(isBlue(crop[5 * 10 + 8]))
    }

    @Test
    fun encodersProduceTheRightContainer() {
        val bmp = BitmapIO.decode(jpeg("g.jpg", 40, 40), 4000)
        val jpg = ByteArrayOutputStream().also { BitmapIO.compress(bmp, OutputFormat.Jpeg(90), it) }.toByteArray()
        assertEquals(0xFF.toByte(), jpg[0]); assertEquals(0xD8.toByte(), jpg[1])
        val png = ByteArrayOutputStream().also { BitmapIO.compress(bmp, OutputFormat.Png, it) }.toByteArray()
        assertEquals("PNG", String(png, 1, 3))
        assertEquals(40, BitmapIO.decodeBytes(BitmapIO.jpegBytes(bmp, 80)).width)
    }

    @Test
    fun atomicWriteReplacesTargetAndCleansUpOnFailure() {
        val target = File(context.cacheDir, "atomic/out.bin")
        AtomicFiles.writeBytes(target, byteArrayOf(1, 2, 3))
        AtomicFiles.writeBytes(target, byteArrayOf(9))
        assertEquals(listOf<Byte>(9), target.readBytes().toList())
        try {
            AtomicFiles.write(target) { it.write(7); throw java.io.IOException("disk full") }
            fail()
        } catch (_: java.io.IOException) { }
        assertEquals("a failed write must not corrupt the existing file", listOf<Byte>(9), target.readBytes().toList())
        assertFalse(File(target.parentFile, "out.bin.tmp").exists())
    }

    @Test
    fun localEngineRendersOnRealBitmapsWithoutTouchingTheSource() = runBlocking {
        val src = BitmapIO.decode(jpeg("h.jpg", 120, 80), 4000)
        val before = src.getPixel(10, 10)
        var analysisCalls = 0
        val engine = LocalEngine(object : FaceAnalysisSource {
            override suspend fun analyze(key: String, image: Bitmap, needSegmentation: Boolean): FaceAnalysis { analysisCalls++; return FaceAnalysis.EMPTY }
        })
        assertTrue(engine.isOffline)
        assertTrue(engine.supports(EditOp.Adjust(Tool.FILTER, 1f))); assertFalse(engine.supports(EditOp.CloudEdit("a", "b", "c")))
        val out = engine.apply(src, listOf(EditOp.Adjust(Tool.FILTER, 1f, variant = "mono"), EditOp.Geometry(quarterTurns = 1)), RenderRequest("k"))
        assertEquals(80, out.width); assertEquals(120, out.height)
        val c = out.getPixel(40, 10)
        assertEquals(Color.red(c), Color.green(c)); assertEquals(Color.green(c), Color.blue(c))
        assertEquals(before, src.getPixel(10, 10)); assertFalse(src.isRecycled)
        assertEquals("global tools need no analysis", 0, analysisCalls)
        engine.apply(src, listOf(EditOp.Adjust(Tool.SKIN_SMOOTH, 1f)), RenderRequest("k"))
        assertEquals(1, analysisCalls)
    }
}
