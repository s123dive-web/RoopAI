package com.suresh.roopai.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.suresh.roopai.data.image.AtomicFiles
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.data.image.OutputFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Saves finished images to the gallery (Pictures/RoopAI) and prepares share intents. */
@Singleton
class MediaSaver @Inject constructor(@ApplicationContext private val context: Context) {

    /** True when the legacy storage permission has to be requested first (Android 8 - 9). */
    val needsLegacyPermission get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun saveToGallery(bitmap: Bitmap, format: OutputFormat): Uri = withContext(Dispatchers.IO) {
        val name = fileName(format)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("gallery refused the new image")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("gallery file could not be opened")).use {
                    BitmapIO.compress(bitmap, format, it)
                }
                // the entry only becomes visible once it is completely written
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            val file = File(dir, name)
            AtomicFiles.write(file) { BitmapIO.compress(bitmap, format, it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(format.mime), null)
            Uri.fromFile(file)
        }
    }

    /** Writes the image to the private share cache and returns a content:// URI for it. */
    suspend fun prepareShare(bitmap: Bitmap, format: OutputFormat): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared")
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
        val file = File(dir, fileName(format))
        AtomicFiles.write(file) { BitmapIO.compress(bitmap, format, it) }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    private fun fileName(format: OutputFormat) =
        "RoopAI_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + "." + format.extension

    companion object {
        const val ALBUM = "RoopAI"
    }
}
