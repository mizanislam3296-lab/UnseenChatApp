package com.example.unseenchat.service

import android.app.Notification
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.unseenchat.data.cache.ReplyActionCache
import com.example.unseenchat.data.cache.ReplyActionPayload
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.local.MediaType
import com.example.unseenchat.util.MediaStorageManager
import com.example.unseenchat.util.WhatsAppVoiceObserverManager
import kotlinx.coroutines.*

class UnseenNotificationService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var voiceObserverManager: WhatsAppVoiceObserverManager? = null

    private val supportedPackages = setOf(
        "com.whatsapp",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.instagram.android"
    )

    override fun onCreate() {
        super.onCreate()
        voiceObserverManager = WhatsAppVoiceObserverManager(applicationContext, serviceScope).also {
            it.startWatching()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in supportedPackages) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (NotificationCompat.isGroupSummary(notification)) return

        val extras = notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val messageText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim() ?: return

        val conversationKey = "${packageName}_${sender}"
        val postTime = sbn.postTime

        extractAndCacheReplyAction(notification, conversationKey)

        val extractedMediaUri = extractMessagingStyleMedia(notification)
        val extractedBitmap = extractPictureBitmap(extras)

        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).chatDao()
            if (dao.isDuplicateMessage(conversationKey, messageText, postTime) > 0) return@launch

            var savedMediaPath: String? = null
            var detectedMediaType = MediaType.TEXT

            if (extractedMediaUri != null) {
                val (uri, mime) = extractedMediaUri
                savedMediaPath = MediaStorageManager.saveFromContentUri(applicationContext, uri, mime)
                detectedMediaType = when {
                    mime?.startsWith("image/") == true -> MediaType.IMAGE
                    mime?.startsWith("audio/") == true -> MediaType.VOICE
                    else -> MediaType.IMAGE
                }
            } else if (extractedBitmap != null) {
                savedMediaPath = MediaStorageManager.saveFromBitmap(applicationContext, extractedBitmap)
                detectedMediaType = MediaType.IMAGE
            } else if (isVoiceMessageNotification(messageText)) {
                detectedMediaType = MediaType.VOICE
            }

            dao.insertMessage(
                ChatMessageEntity(
                    conversationKey = conversationKey,
                    packageName = packageName,
                    senderName = sender,
                    messageText = messageText,
                    timestamp = postTime,
                    isOutgoing = false,
                    mediaType = detectedMediaType,
                    localMediaPath = savedMediaPath
                )
            )
        }
    }

    private fun extractAndCacheReplyAction(notification: Notification, conversationKey: String) {
        val actionCount = NotificationCompat.getActionCount(notification)
        for (i in 0 until actionCount) {
            val action = NotificationCompat.getAction(notification, i) ?: continue
            val remoteInputs = action.remoteInputs
            val actionIntent = action.actionIntent

            if (!remoteInputs.isNullOrEmpty() && actionIntent != null) {
                ReplyActionCache.putReplyAction(
                    conversationKey = conversationKey,
                    payload = ReplyActionPayload(
                        pendingIntent = actionIntent,
                        remoteInputs = remoteInputs
                    )
                )
                break
            }
        }
    }

    private fun extractMessagingStyleMedia(notification: Notification): Pair<Uri, String?>? {
        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification) ?: return null

        val lastMessage = messagingStyle.messages.lastOrNull() ?: return null
        val uri = lastMessage.dataUri ?: return null
        return Pair(uri, lastMessage.dataMimeType)
    }

    @Suppress("DEPRECATION")
    private fun extractPictureBitmap(extras: Bundle): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
        }
    }

    private fun isVoiceMessageNotification(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("voice message") ||
                lower.contains("audio") ||
                text.contains("🎤") ||
                lower.contains("ভয়েস মেসেজ")
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceObserverManager?.stopWatching()
        serviceScope.cancel()
    }
}
