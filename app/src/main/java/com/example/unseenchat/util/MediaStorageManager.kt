package com.example.unseenchat.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaStorageManager {

    private const val MEDIA_DIR_NAME = "unseen_media"

    private fun getMediaDirectory(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveFromContentUri(
        context: Context,
        contentUri: Uri,
        mimeType: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType) ?: "jpg"
            val fileName = "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
            val destFile = File(getMediaDirectory(context), fileName)

            context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveFromBitmap(
        context: Context,
        bitmap: Bitmap
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(getMediaDirectory(context), fileName)

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun backupExternalMediaFile(
        context: Context,
        sourceFile: File
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null
            val destFile = File(getMediaDirectory(context), "vn_${System.currentTimeMillis()}_${sourceFile.name}")
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun cleanUpOldMedia(context: Context, maxAgeDays: Int = 7) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        val dir = getMediaDirectory(context)
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }
}
