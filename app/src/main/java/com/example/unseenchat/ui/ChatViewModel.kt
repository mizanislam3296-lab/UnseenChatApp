package com.example.unseenchat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unseenchat.data.cache.ReplyResult
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    // ফিল্টার ছাড়া সব ইনকামিং ও আউটগোয়িং মেসেজ পর্যবেক্ষণ
    val messages: StateFlow<List<ChatMessageEntity>> = repository
        .observeAllMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _replyResult = MutableSharedFlow<ReplyResult>()
    val replyResult: SharedFlow<ReplyResult> = _replyResult.asSharedFlow()

    fun sendDirectReply(conversationKey: String, packageName: String, senderName: String, replyText: String) {
        if (replyText.isBlank()) return

        viewModelScope.launch {
            val result = repository.sendReply(
                conversationKey = conversationKey,
                packageName = packageName,
                senderName = senderName,
                replyText = replyText.trim()
            )
            _replyResult.emit(result)
        }
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("অপরিচিত ViewModel ক্লাস")
    }
}
