package com.example.unseenchat.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvChatTitle: TextView

    private val chatAdapter = ChatAdapter()
    private val repository by lazy { ChatRepository(applicationContext) }
    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    private var conversationKey: String = ""
    private var packageName: String = ""
    private var senderName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        conversationKey = intent.getStringExtra("EXTRA_CONVERSATION_KEY") ?: ""
        packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: ""
        senderName = intent.getStringExtra("EXTRA_SENDER_NAME") ?: "Chat"

        initViews()
        setupRecyclerView()
        observeMessages()
        setupSendButton()
    }

    private fun initViews() {
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)
        tvChatTitle = findViewById(R.id.tvChatTitle)

        tvChatTitle.text = senderName
    }

    private fun setupRecyclerView() {
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = chatAdapter
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            database.chatDao().getMessagesForConversation(conversationKey).collectLatest { list ->
                chatAdapter.submitList(list) {
                    if (list.isNotEmpty()) {
                        rvMessages.scrollToPosition(list.size - 1)
                    }
                }
            }
        }
    }

    private fun setupSendButton() {
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val success = repository.sendReply(conversationKey, packageName, senderName, text)
                if (success) {
                    etMessage.text.clear()
                } else {
                    Toast.makeText(
                        this@ChatActivity,
                        "হোয়াটসঅ্যাপের রিপ্লাই সেশন শেষ হয়ে গেছে। নতুন নোটিফিকেশন আসা পর্যন্ত অপেক্ষা করুন।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
