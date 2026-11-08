package com.example.unseenchat.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.unseenchat.ai.GeminiAiHelper
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.MessageEntity
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnseenNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { ChatRepository(applicationContext) }

    private val supportedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "org.telegram.messenger"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (!supportedPackages.contains(packageName)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        if (title.isBlank() || text.isBlank()) return

        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val conversationKey = "${packageName}_$title"
        val timestamp = sbn.postTime

        extractAndSaveReplyAction(sbn, conversationKey)

        val messageEntity = MessageEntity(
            conversationKey = conversationKey,
            packageName = packageName,
            senderName = title,
            messageText = text,
            timestamp = timestamp,
            isOutgoing = false
        )

        serviceScope.launch {
            database.chatDao().insertMessage(messageEntity)

            val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
            val isOfflineReplyEnabled = prefs.getBoolean("OFFLINE_REPLY_ENABLED", false)
            val offlineReplyText = prefs.getString("OFFLINE_REPLY_TEXT", "আমি এখন অফলাইনে আছি, ফিরে এসে মেসেজ দেব।") ?: ""
            val isAiAutoReplyEnabled = prefs.getBoolean("AI_AUTO_REPLY_ENABLED", false)

            delay(1000)

            // ১. যদি অফলাইন রিপ্লাই অন থাকে, তবে আপনার কাস্টম টেক্সট পাঠানো হবে
            if (isOfflineReplyEnabled && offlineReplyText.isNotBlank()) {
                repository.sendReply(conversationKey, packageName, title, offlineReplyText)
            } 
            // ২. অন্যথায় যদি AI অটো রিপ্লাই অন থাকে, তবে জেমিনি AI উত্তর দেবে
            else if (isAiAutoReplyEnabled) {
                val aiReply = GeminiAiHelper.getAutoReplyText(text)
                if (!aiReply.isNullOrBlank()) {
                    repository.sendReply(conversationKey, packageName, title, aiReply)
                }
            }
        }
    }

    private fun extractAndSaveReplyAction(sbn: StatusBarNotification, conversationKey: String) {
        val actions = NotificationCompat.getActionCount(sbn.notification)
        for (i in 0 until actions) {
            val action = NotificationCompat.getAction(sbn.notification, i) ?: continue
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput) {
                    ChatRepository.replyActionMap[conversationKey] = action
                    return
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
