package com.example.unseenchat.data.repository

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.example.unseenchat.data.cache.ReplyActionCache
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)

    suspend fun sendReply(
        conversationKey: String,
        packageName: String,
        senderName: String,
        replyText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = ReplyActionCache.getReplyAction(conversationKey) ?: return@withContext false

        try {
            val replyIntent = Intent()
            val bundle = Bundle()

            for (remoteInput in payload.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(payload.remoteInputs, replyIntent, bundle)

            // মেসেজ সেন্ড এক্সিকিউশন
            payload.pendingIntent.send(context, 0, replyIntent)

            // ডাটাবেসে আউটগোয়িং মেসেজ সেভ করা
            val entity = ChatMessageEntity(
                conversationKey = conversationKey,
                packageName = packageName,
                senderName = senderName,
                messageText = replyText,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                mediaType = MediaType.TEXT
            )
            database.chatDao().insertMessage(entity)

            // বিশেষ দ্রষ্টব্য: এখানে ReplyActionCache.removeReplyAction() কল করা হয়নি, 
            // যাতে পরপর একাধিক মেসেজ পাঠানো সম্ভব হয়।
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
