package com.example.unseenchat.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.cache.ReplyResult
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.local.ChatMessageEntity
import com.example.unseenchat.data.repository.ChatRepository
import com.example.unseenchat.util.NotificationPermissionHelper
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var etReplyInput: EditText
    private lateinit var btnSendReply: Button
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var viewModel: ChatViewModel

    private var latestMessage: ChatMessageEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val database = AppDatabase.getInstance(applicationContext)
        val repository = ChatRepository(database.chatDao(), applicationContext)
        val factory = ChatViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        checkNotificationPermission()
        initViews()
        setupRecyclerView()
        observeViewModel()
        setupListeners()
    }

    private fun checkNotificationPermission() {
        if (!NotificationPermissionHelper.isNotificationAccessGranted(this)) {
            val intent = NotificationPermissionHelper.createNotificationSettingsIntent(this)
            Toast.makeText(this, "মেসেজ পড়তে নোটিফিকেশন অ্যাক্সেস চালু করুন", Toast.LENGTH_LONG).show()
            startActivity(intent)
        }
    }

    private fun initViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages)
        etReplyInput = findViewById(R.id.etReplyInput)
        btnSendReply = findViewById(R.id.btnSendReply)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = chatAdapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messageList ->
                        chatAdapter.submitList(messageList) {
                            if (messageList.isNotEmpty()) {
                                latestMessage = messageList.lastOrNull { !it.isOutgoing }
                                rvChatMessages.scrollToPosition(messageList.size - 1)
                            }
                        }
                    }
                }

                launch {
                    viewModel.replyResult.collect { result ->
                        when (result) {
                            is ReplyResult.Success -> {
                                etReplyInput.text.clear()
                                Toast.makeText(this@ChatActivity, "রিপ্লাই সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                            is ReplyResult.SessionExpired -> {
                                Toast.makeText(this@ChatActivity, "সেশন শেষ হয়েছে, নতুন মেসেজের অপেক্ষা করুন", Toast.LENGTH_LONG).show()
                            }
                            is ReplyResult.ActionNotFound -> {
                                Toast.makeText(this@ChatActivity, "ডাইরেক্ট রিপ্লাই অ্যাকশন পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                            }
                            is ReplyResult.Error -> {
                                Toast.makeText(this@ChatActivity, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        btnSendReply.setOnClickListener {
            val replyText = etReplyInput.text.toString().trim()
            val target = latestMessage

            if (replyText.isNotEmpty()) {
                if (target != null) {
                    viewModel.sendDirectReply(
                        conversationKey = target.conversationKey,
                        packageName = target.packageName,
                        senderName = target.senderName,
                        replyText = replyText
                    )
                } else {
                    Toast.makeText(this, "রিপ্লাই দেওয়ার মতো কোনো ইনকামিং মেসেজ নেই", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatAdapter.releasePlayer()
    }
}
