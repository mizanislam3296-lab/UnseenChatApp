package com.example.unseenchat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChatRepository(application)

    fun getMessages(conversationKey: String): Flow<List<ChatMessageEntity>> {
        return database.chatDao().getMessagesForConversation(conversationKey)
    }

    fun sendMessage(
        conversationKey: String,
        packageName: String,
        senderName: String,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.sendReply(conversationKey, packageName, senderName, text)
            onResult(success)
        }
    }
}
