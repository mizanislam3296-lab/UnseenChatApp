package com.example.unseenchat.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.unseenchat.data.cache.ReplyActionCache
import com.example.unseenchat.data.cache.ReplyActionPayload
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.local.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UnseenNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    companion object {
        // সমর্থিত সোশ্যাল প্ল্যাটফর্মসমূহ
        val SUPPORTED_PACKAGES = setOf(
            "com.whatsapp",            // WhatsApp
            "com.whatsapp.w4b",        // WhatsApp Business
            "com.facebook.orca",       // Messenger
            "org.telegram.messenger",  // Telegram
            "com.instagram.android"    // Instagram
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!SUPPORTED_PACKAGES.contains(packageName)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val rawTitle = extras.getString(Notification.EXTRA_TITLE) ?: ""
        if (rawTitle.isBlank()) return
        val title = rawTitle.trim()

        // প্ল্যাটফর্ম ও প্রেরকের নাম দিয়ে নির্দিষ্ট Conversation Key তৈরি
        val conversationKey = "${packageName}_${title}"

        // ডাইরেক্ট রিপ্লাই কি-ক্যাশ করা
        cacheDirectReply(conversationKey, notification)

        // MessagingStyle থেকে একের পর এক আসা সব মেসেজ নিষ্কাশন (যাতে কোনো মেসেজ মিস না হয়)
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

        serviceScope.launch {
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                for (msg in messagingStyle.messages) {
                    val text = msg.text?.toString()?.trim() ?: continue
                    if (text.isEmpty()) continue

                    val sender = msg.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: title
                    val time = if (msg.timestamp > 0) msg.timestamp else System.currentTimeMillis()
                    val mediaType = if (text.contains("Voice message", ignoreCase = true) || text.contains("ভয়েস", ignoreCase = true)) {
                        MediaType.VOICE
                    } else {
                        MediaType.TEXT
                    }

                    if (!database.chatDao().isDuplicateMessage(conversationKey, text, time)) {
                        val entity = ChatMessageEntity(
                            conversationKey = conversationKey,
                            packageName = packageName,
                            senderName = sender,
                            messageText = text,
                            timestamp = time,
                            isOutgoing = false,
                            mediaType = mediaType
                        )
                        database.chatDao().insertMessage(entity)
                    }
                }
            } else {
                // সাধারণ সিঙ্গেল মেসেজ ফলব্যাক
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    val time = notification.`when`.takeIf { it > 0 } ?: System.currentTimeMillis()
                    val mediaType = if (text.contains("Voice message", ignoreCase = true) || text.contains("ভয়েস", ignoreCase = true)) {
                        MediaType.VOICE
                    } else {
                        MediaType.TEXT
                    }

                    if (!database.chatDao().isDuplicateMessage(conversationKey, text, time)) {
                        val entity = ChatMessageEntity(
                            conversationKey = conversationKey,
                            packageName = packageName,
                            senderName = title,
                            messageText = text,
                            timestamp = time,
                            isOutgoing = false,
                            mediaType = mediaType
                        )
                        database.chatDao().insertMessage(entity)
                    }
                }
            }
        }
    }

    private fun cacheDirectReply(conversationKey: String, notification: Notification) {
        val actionsCount = NotificationCompat.getActionCount(notification)
        for (i in 0 until actionsCount) {
            val action = NotificationCompat.getAction(notification, i) ?: continue
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty() && action.actionIntent != null) {
                val payload = ReplyActionPayload(
                    pendingIntent = action.actionIntent,
                    remoteInputs = remoteInputs
                )
                ReplyActionCache.putReplyAction(conversationKey, payload)
                return
            }
        }

        // WearableExtender ফলব্যাক
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for (action in wearableExtender.actions) {
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty() && action.actionIntent != null) {
                val payload = ReplyActionPayload(
                    pendingIntent = action.actionIntent,
                    remoteInputs = remoteInputs
                )
                ReplyActionCache.putReplyAction(conversationKey, payload)
                return
            }
        }
    }
}
