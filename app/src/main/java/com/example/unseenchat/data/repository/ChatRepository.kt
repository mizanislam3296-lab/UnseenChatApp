package com.example.unseenchat.data.repository

import android.content.Context
import com.example.unseenchat.data.cache.ReplyActionCache
import com.example.unseenchat.data.cache.ReplyResult
import com.example.unseenchat.data.local.ChatDao
import com.example.unseenchat.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val appContext: Context
) {
    fun observeMessages(conversationKey: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesByConversation(conversationKey)
    }

    suspend fun sendReply(
        conversationKey: String,
        packageName: String,
        senderName: String,
        replyText: String
    ): ReplyResult = withContext(Dispatchers.IO) {
        val result = ReplyActionCache.executeDirectReply(
            context = appContext,
            conversationKey = conversationKey,
            replyText = replyText
        )

        if (result is ReplyResult.Success) {
            chatDao.insertMessage(
                ChatMessageEntity(
                    conversationKey = conversationKey,
                    packageName = packageName,
                    senderName = senderName,
                    messageText = replyText,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true
                )
            )
        }
        result
    }
}
