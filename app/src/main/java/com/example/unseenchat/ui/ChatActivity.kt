package com.example.unseenchat.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.cache.ReplyResult
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var etReplyMessage: EditText
    private lateinit var btnSendReply: Button
    private lateinit var tvChatTitle: TextView

    private lateinit var chatAdapter: ChatAdapter
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { ChatRepository(database.chatDao(), applicationContext) }

    private var conversationKey: String = ""
    private var packageName: String = ""
    private var senderName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        conversationKey = intent.getStringExtra("EXTRA_CONVERSATION_KEY") ?: ""
        packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: "com.whatsapp"
        senderName = intent.getStringExtra("EXTRA_SENDER_NAME") ?: "Chat"

        initViews()
        setupRecyclerView()
        loadMessages()
        setupReplyAction()
    }

    private fun initViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages)
        etReplyMessage = findViewById(R.id.etReplyMessage)
        btnSendReply = findViewById(R.id.btnSendReply)
        tvChatTitle = findViewById(R.id.tvChatTitle)
        tvChatTitle.text = senderName
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = chatAdapter
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            // শুধুমাত্র এই নির্দিষ্ট ব্যক্তির সব মেসেজ লোড হবে
            database.chatDao().getMessagesForConversation(conversationKey).collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        rvChatMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    private fun setupReplyAction() {
        btnSendReply.setOnClickListener {
            val text = etReplyMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val result = repository.sendReply(conversationKey, packageName, senderName, text)
                when (result) {
                    is ReplyResult.Success -> {
                        etReplyMessage.text.clear()
                    }
                    is ReplyResult.SessionExpired -> {
                        Toast.makeText(this@ChatActivity, "সেশন শেষ হয়েছে। নতুন মেসেজ আসা পর্যন্ত অপেক্ষা করুন।", Toast.LENGTH_SHORT).show()
                    }
                    is ReplyResult.ActionNotFound -> {
                        Toast.makeText(this@ChatActivity, "রিপ্লাই বাটন পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
                    }
                    is ReplyResult.Error -> {
                        Toast.makeText(this@ChatActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
