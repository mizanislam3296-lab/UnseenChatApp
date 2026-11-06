package com.example.unseenchat.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.local.AppDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvConversations: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var conversationAdapter: ConversationAdapter

    private lateinit var tabWhatsApp: TextView
    private lateinit var tabMessenger: TextView
    private lateinit var tabTelegram: TextView

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private var loadJob: Job? = null
    private var selectedPackage = "com.whatsapp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupPlatformTabs()
        loadConversations(selectedPackage)
    }

    private fun initViews() {
        rvConversations = findViewById(R.id.rvConversations)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tabWhatsApp = findViewById(R.id.tabWhatsApp)
        tabMessenger = findViewById(R.id.tabMessenger)
        tabTelegram = findViewById(R.id.tabTelegram)
    }

    private fun setupRecyclerView() {
        conversationAdapter = ConversationAdapter { summary ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("EXTRA_CONVERSATION_KEY", summary.conversationKey)
                putExtra("EXTRA_PACKAGE_NAME", summary.packageName)
                putExtra("EXTRA_SENDER_NAME", summary.senderName)
            }
            startActivity(intent)
        }
        rvConversations.layoutManager = LinearLayoutManager(this)
        rvConversations.adapter = conversationAdapter
    }

    private fun setupPlatformTabs() {
        tabWhatsApp.setOnClickListener {
            selectedPackage = "com.whatsapp"
            updateTabStyle(tabWhatsApp)
            loadConversations(selectedPackage)
        }

        tabMessenger.setOnClickListener {
            selectedPackage = "com.facebook.orca"
            updateTabStyle(tabMessenger)
            loadConversations(selectedPackage)
        }

        tabTelegram.setOnClickListener {
            selectedPackage = "org.telegram.messenger"
            updateTabStyle(tabTelegram)
            loadConversations(selectedPackage)
        }
    }

    private fun updateTabStyle(activeTab: TextView) {
        val tabs = listOf(tabWhatsApp, tabMessenger, tabTelegram)
        for (tab in tabs) {
            if (tab == activeTab) {
                tab.setBackgroundColor(Color.WHITE)
                tab.setTextColor(Color.parseColor("#00A884"))
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT)
                tab.setTextColor(Color.WHITE)
            }
        }
    }

    private fun loadConversations(packageName: String) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            database.chatDao().getConversationsByPackage(packageName).collectLatest { list ->
                if (list.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    rvConversations.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvConversations.visibility = View.VISIBLE
                    conversationAdapter.submitList(list)
                }
            }
        }
    }
}
