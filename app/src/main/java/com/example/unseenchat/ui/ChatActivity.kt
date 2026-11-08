package com.example.unseenchat.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.ai.GeminiAiHelper
import com.example.unseenchat.data.local.AppDatabase
import com.example.unseenchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnAiGenerate: Button
    private lateinit var switchAutoReply: SwitchCompat
    private lateinit var tvChatTitle: TextView
    private lateinit var layoutSuggestions: View
    private lateinit var containerSuggestions: LinearLayout

    private val chatAdapter = ChatAdapter()
    private val repository by lazy { ChatRepository(applicationContext) }
    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    private var conversationKey: String = ""
    private var packageName: String = ""
    private var senderName: String = ""
    private var lastIncomingMessage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        conversationKey = intent.getStringExtra("EXTRA_CONVERSATION_KEY") ?: ""
        packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: ""
        senderName = intent.getStringExtra("EXTRA_SENDER_NAME") ?: "Chat"

        initViews()
        setupRecyclerView()
        observeMessages()
        setupListeners()
    }

    private fun initViews() {
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)
        btnAiGenerate = findViewById(R.id.btnAiGenerate)
        switchAutoReply = findViewById(R.id.switchAutoReply)
        tvChatTitle = findViewById(R.id.tvChatTitle)
        layoutSuggestions = findViewById(R.id.layoutSuggestions)
        containerSuggestions = findViewById(R.id.containerSuggestions)

        tvChatTitle.text = senderName

        // সংরক্ষিত অটো রিপ্লাই সেটিং রিড করা
        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        switchAutoReply.isChecked = prefs.getBoolean("AI_AUTO_REPLY_ENABLED", false)
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

                // সর্বশেষ ইনকামিং টেক্সট মেসেজ ট্র্যাক করা
                val incoming = list.lastOrNull { !it.isOutgoing }
                if (incoming != null) {
                    lastIncomingMessage = incoming.messageText
                }
            }
        }
    }

    private fun setupListeners() {
        // ১. অটো রিপ্লাই সুইচ পরিবর্তন
        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("AI_AUTO_REPLY_ENABLED", isChecked).apply()
            val stateText = if (isChecked) "AI Auto-Reply চালু হয়েছে" else "AI Auto-Reply বন্ধ করা হয়েছে"
            Toast.makeText(this, stateText, Toast.LENGTH_SHORT).show()
        }

        // ২. ✨ AI বাটনে চাপ দিয়ে ম্যানুয়াল সাজেশন আনা
        btnAiGenerate.setOnClickListener {
            if (lastIncomingMessage.isBlank()) {
                Toast.makeText(this, "উত্তর দেওয়ার মতো মেসেজ নেই!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "AI উত্তর তৈরি করছে...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val suggestions = GeminiAiHelper.getSmartReplies(lastIncomingMessage)
                containerSuggestions.removeAllViews()

                if (suggestions.isNotEmpty()) {
                    layoutSuggestions.visibility = View.VISIBLE
                    for (replyText in suggestions) {
                        val btn = Button(this@ChatActivity).apply {
                            text = replyText
                            textSize = 12f
                            isAllCaps = false
                            setBackgroundColor(Color.parseColor("#FFFFFF"))
                            setTextColor(Color.parseColor("#00796B"))
                            setPadding(20, 6, 20, 6)
                            setOnClickListener {
                                etMessage.setText(replyText)
                                etMessage.setSelection(replyText.length)
                            }
                        }
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(6, 0, 6, 0) }

                        containerSuggestions.addView(btn, params)
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "ইন্টারনেট কানেকশন চেক করুন!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ৩. সেন্ড বাটনে ক্লিক করে মেসেজ পাঠানো
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val success = repository.sendReply(conversationKey, packageName, senderName, text)
                if (success) {
                    etMessage.text.clear()
                    layoutSuggestions.visibility = View.GONE
                } else {
                    Toast.makeText(
                        this@ChatActivity,
                        "রিপ্লাই সেশন শেষ হয়ে গেছে। পরবর্তী মেসেজ আসা পর্যন্ত অপেক্ষা করুন।",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
