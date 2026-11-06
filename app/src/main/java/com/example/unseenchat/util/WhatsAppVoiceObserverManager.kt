package com.example.unseenchat.util

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import com.example.unseenchat.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class WhatsAppVoiceObserverManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val activeObservers = mutableListOf<FileObserver>()

    fun startWatching() {
        stopWatching()

        val rootStorage = Environment.getExternalStorageDirectory()
        val possibleVoiceDirs = listOf(
            File(rootStorage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(rootStorage, "WhatsApp/Media/WhatsApp Voice Notes")
        )

        for (voiceDir in possibleVoiceDirs) {
            if (voiceDir.exists() && voiceDir.isDirectory) {
                val latestSubDir = voiceDir.listFiles { file -> file.isDirectory }
                    ?.maxByOrNull { it.lastModified() }

                if (latestSubDir != null) {
                    watchDirectory(latestSubDir)
                }
                watchParentForNewSubfolders(voiceDir)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun watchDirectory(targetDir: File) {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        val observer = object : FileObserver(targetDir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || !path.endsWith(".opus", ignoreCase = true)) return

                val sourceFile = File(targetDir, path)
                scope.launch {
                    val backupPath = MediaStorageManager.backupExternalMediaFile(context, sourceFile)
                    if (backupPath != null) {
                        val dao = AppDatabase.getInstance(context).chatDao()
                        dao.attachVoiceFileToLatestPendingMessage("com.whatsapp", backupPath)
                    }
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)
    }

    @Suppress("DEPRECATION")
    private fun watchParentForNewSubfolders(parentDir: File) {
        val observer = object : FileObserver(parentDir.absolutePath, FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val newSubDir = File(parentDir, path)
                if (newSubDir.isDirectory) {
                    watchDirectory(newSubDir)
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)
    }

    fun stopWatching() {
        activeObservers.forEach { it.stopWatching() }
        activeObservers.clear()
    }
}
