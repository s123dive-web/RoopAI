package com.suresh.roopai.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.suresh.roopai.data.image.AtomicFiles
import com.suresh.roopai.data.image.BitmapIO
import com.suresh.roopai.edit.EditException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files of an edit project: `files/projects/<id>/original` (untouched bytes of the picked
 * photo, EXIF included), `thumb.jpg`, cached AI results and background photos.
 */
@Singleton
class ProjectStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root get() = File(context.filesDir, "projects")

    fun dir(projectId: String) = File(root, projectId)
    fun original(projectId: String) = File(dir(projectId), ORIGINAL)
    fun thumb(projectId: String) = File(dir(projectId), THUMB)
    fun file(projectId: String, name: String) = File(dir(projectId), File(name).name)

    /** Copies the picked image into a new project. Returns the project id. */
    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val target = original(id)
        try {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw EditException.Unreadable(e.message ?: "no access")
            } ?: throw EditException.Unreadable("no access")
            input.use { src -> AtomicFiles.write(target) { src.copyTo(it, 64 * 1024) } }
            BitmapIO.info(target) // validates that it is a decodable image
            id
        } catch (e: Exception) {
            dir(id).deleteRecursively()
            throw if (e is EditException) e else EditException.Unreadable(e.message ?: "")
        }
    }

    /** Stores bytes under a fresh name with the given prefix; returns the file name. */
    suspend fun addFile(projectId: String, prefix: String, extension: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val name = "${prefix}_${System.currentTimeMillis()}.$extension"
        AtomicFiles.writeBytes(file(projectId, name), bytes)
        name
    }

    suspend fun saveThumb(projectId: String, preview: Bitmap) = withContext(Dispatchers.IO) {
        if (!dir(projectId).exists()) return@withContext
        val small = BitmapIO.scaleToFit(preview, 360)
        try {
            AtomicFiles.write(thumb(projectId)) { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } finally {
            if (small !== preview) small.recycle()
        }
    }

    /** Deletes cached files that the saved edit stack no longer references. */
    suspend fun prune(projectId: String, keep: Set<String>) = withContext(Dispatchers.IO) {
        dir(projectId).listFiles()?.forEach { f ->
            if (f.name != ORIGINAL && f.name != THUMB && f.name !in keep) f.delete()
        }
    }

    suspend fun delete(projectId: String) = withContext(Dispatchers.IO) { dir(projectId).deleteRecursively() }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { root.deleteRecursively() }

    companion object {
        const val ORIGINAL = "original"
        const val THUMB = "thumb.jpg"
    }
}
