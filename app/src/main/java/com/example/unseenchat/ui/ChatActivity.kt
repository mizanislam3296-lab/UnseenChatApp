package com.example.unseenchat.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
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

    private lateinit var rootChatLayout: ConstraintLayout
    private lateinit var headerBar: LinearLayout
    private lateinit var layoutInputBar: LinearLayout
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnAiGenerate: Button
    private lateinit var btnOfflineSettings: Button
    private lateinit var btnThemeToggle: Button
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
        applySavedThemeMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        conversationKey = intent.getStringExtra("EXTRA_CONVERSATION_KEY") ?: ""
        packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: ""
        senderName = intent.getStringExtra("EXTRA_SENDER_NAME") ?: "Chat"

        initViews()
        applyDynamicColors()
        setupRecyclerView()
        observeMessages()
        setupListeners()
    }

    private fun initViews() {
        rootChatLayout = findViewById(R.id.rootChatLayout)
        headerBar = findViewById(R.id.headerBar)
        layoutInputBar = findViewById(R.id.layoutInputBar)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)
        btnAiGenerate = findViewById(R.id.btnAiGenerate)
        btnOfflineSettings = findViewById(R.id.btnOfflineSettings)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        switchAutoReply = findViewById(R.id.switchAutoReply)
        tvChatTitle = findViewById(R.id.tvChatTitle)
        layoutSuggestions = findViewById(R.id.layoutSuggestions)
        containerSuggestions = findViewById(R.id.containerSuggestions)

        tvChatTitle.text = senderName

        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        switchAutoReply.isChecked = prefs.getBoolean("AI_AUTO_REPLY_ENABLED", false)
    }

    private fun applySavedThemeMode() {
        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        when (prefs.getInt("APP_THEME_MODE", 2)) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun isDarkModeActive(): Boolean {
        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("APP_THEME_MODE", 2)
        return when (savedMode) {
            1 -> true
            0 -> false
            else -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun applyDynamicColors() {
        if (isDarkModeActive()) {
            // ডার্ক মোড কালার (WhatsApp Dark Style)
            rootChatLayout.setBackgroundColor(Color.parseColor("#0B141A"))
            headerBar.setBackgroundColor(Color.parseColor("#1F2C34"))
            layoutInputBar.setBackgroundColor(Color.parseColor("#1F2C34"))
            layoutSuggestions.setBackgroundColor(Color.parseColor("#182229"))
            etMessage.setTextColor(Color.parseColor("#E9EDEF"))
            etMessage.setHintTextColor(Color.parseColor("#8696A0"))
            btnThemeToggle.text = "☀️"
        } else {
            // ডে (লাইট) মোড কালার
            rootChatLayout.setBackgroundColor(Color.parseColor("#EFEAE2"))
            headerBar.setBackgroundColor(Color.parseColor("#00A884"))
            layoutInputBar.setBackgroundColor(Color.parseColor("#FFFFFF"))
            layoutSuggestions.setBackgroundColor(Color.parseColor("#E0E0E0"))
            etMessage.setTextColor(Color.parseColor("#111B21"))
            etMessage.setHintTextColor(Color.parseColor("#8696A0"))
            btnThemeToggle.text = "🌙"
        }
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

                val incoming = list.lastOrNull { !it.isOutgoing }
                if (incoming != null) {
                    lastIncomingMessage = incoming.messageText
                }
            }
        }
    }

    private fun setupListeners() {
        // ১. AI অটো রিপ্লাই সুইচ
        switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("AI_AUTO_REPLY_ENABLED", isChecked).apply()
            val stateText = if (isChecked) "AI Auto-Reply চালু হয়েছে" else "AI Auto-Reply বন্ধ করা হয়েছে"
            Toast.makeText(this, stateText, Toast.LENGTH_SHORT).show()
        }

        // ২. অফলাইন মেসেজ সেটিংস ডায়ালগ
        btnOfflineSettings.setOnClickListener {
            showOfflineSettingsDialog()
        }

        // ৩. ডার্ক ও ডে মোড থিম পরিবর্তন ডায়ালগ
        btnThemeToggle.setOnClickListener {
            showThemeSelectionDialog()
        }

        // ৪. ✨ AI বাটন ক্লিক করে সাজেশন তৈরি
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
                    val dark = isDarkModeActive()
                    for (replyText in suggestions) {
                        val btn = Button(this@ChatActivity).apply {
                            text = replyText
                            textSize = 12f
                            isAllCaps = false
                            setBackgroundColor(if (dark) Color.parseColor("#2A3942") else Color.parseColor("#FFFFFF"))
                            setTextColor(if (dark) Color.parseColor("#00A884") else Color.parseColor("#00796B"))
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

        // ৫. সেন্ড বাটন
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

    private fun showThemeSelectionDialog() {
        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getInt("APP_THEME_MODE", 2)
        val themes = arrayOf("☀️ ডে মোড (Light)", "🌙 ডার্ক মোড (Dark)", "📱 সিস্টেম ডিফল্ট (System)")

        AlertDialog.Builder(this)
            .setTitle("থিম সেট করুন")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                prefs.edit().putInt("APP_THEME_MODE", which).apply()
                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                applyDynamicColors()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showOfflineSettingsDialog() {
        val prefs = getSharedPreferences("UnseenChatPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("OFFLINE_REPLY_ENABLED", false)
        val savedText = prefs.getString("OFFLINE_REPLY_TEXT", "আমি এখন অফলাইনে আছি, ফিরে এসে মেসেজ দেব।")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val switchView = SwitchCompat(this).apply {
            text = "অফলাইন রিপ্লাই চালু রাখুন"
            isChecked = isEnabled
            textSize = 15f
        }

        val input = EditText(this).apply {
            hint = "আপনার কাস্টম মেসেজ লিখুন..."
            setText(savedText)
            textSize = 14f
            setPadding(0, 30, 0, 20)
        }

        layout.addView(switchView)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("অফলাইন কাস্টম মেসেজ")
            .setView(layout)
            .setPositiveButton("সেভ করুন") { _, _ ->
                val newText = input.text.toString().trim()
                val newEnabled = switchView.isChecked
                prefs.edit()
                    .putBoolean("OFFLINE_REPLY_ENABLED", newEnabled)
                    .putString("OFFLINE_REPLY_TEXT", if (newText.isEmpty()) "আমি এখন অফলাইনে আছি।" else newText)
                    .apply()

                val status = if (newEnabled) "চালু হয়েছে" else "বন্ধ করা হয়েছে"
                Toast.makeText(this, "অফলাইন রিপ্লাই $status", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
