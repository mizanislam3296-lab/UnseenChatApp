package com.example.unseenchat.data.repository

import android.content.Context
import com.example.unseenchat.data.cache.ReplyActionCache
import com.example.unseenchat.data.cache.ReplyResult
import com.example.unseenchat.data.local.ChatDao
import com.example.unseenchat.data.local.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val context: Context
) {
    // সব মেসেজ পাওয়ার জন্য
    fun observeAllMessages(): Flow<List<ChatMessageEntity>> {
        return chatDao.getAllMessages()
    }

    suspend fun sendReply(
        conversationKey: String,
        packageName: String,
        senderName: String,
        replyText: String
    ): ReplyResult {
        // সঠিক মেথড ও প্যারামিটার কল
        val result = ReplyActionCache.executeDirectReply(context, conversationKey, replyText)
        if (result is ReplyResult.Success) {
            val outgoingMessage = ChatMessageEntity(
                conversationKey = conversationKey,
                packageName = packageName,
                senderName = senderName,
                messageText = replyText,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true
            )
            chatDao.insertMessage(outgoingMessage)
        }
        return result
    }
}
