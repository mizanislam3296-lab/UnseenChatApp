package com.example.unseenchat.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.unseenchat.R
import com.example.unseenchat.data.local.AppDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rootMainLayout: LinearLayout
    private lateinit var headerMainBar: LinearLayout
    private lateinit var tabContainerBar: LinearLayout
    private lateinit var btnMainThemeToggle: TextView

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
        applySavedThemeMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        applyMainDynamicColors()
        setupRecyclerView()
        setupPlatformTabs()
        loadConversations(selectedPackage)
    }

    override fun onResume() {
        super.onResume()
        applyMainDynamicColors()
        updateActiveTabByPackage()
    }

    private fun initViews() {
        rootMainLayout = findViewById(R.id.rootMainLayout)
        headerMainBar = findViewById(R.id.headerMainBar)
        tabContainerBar = findViewById(R.id.tabContainerBar)
        btnMainThemeToggle = findViewById(R.id.btnMainThemeToggle)

        rvConversations = findViewById(R.id.rvConversations)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tabWhatsApp = findViewById(R.id.tabWhatsApp)
        tabMessenger = findViewById(R.id.tabMessenger)
        tabTelegram = findViewById(R.id.tabTelegram)

        btnMainThemeToggle.setOnClickListener {
            showThemeSelectionDialog()
        }
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
        return when (prefs.getInt("APP_THEME_MODE", 2)) {
            1 -> true
            0 -> false
            else -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun applyMainDynamicColors() {
        if (isDarkModeActive()) {
            rootMainLayout.setBackgroundColor(Color.parseColor("#111B21"))
            headerMainBar.setBackgroundColor(Color.parseColor("#1F2C34"))
            tabContainerBar.setBackgroundColor(Color.parseColor("#1F2C34"))
            tvEmptyState.setTextColor(Color.parseColor("#8696A0"))
            btnMainThemeToggle.text = "☀️ ডে মোড"
            btnMainThemeToggle.setBackgroundColor(Color.parseColor("#2A3942"))
        } else {
            rootMainLayout.setBackgroundColor(Color.parseColor("#F7F8FA"))
            headerMainBar.setBackgroundColor(Color.parseColor("#00A884"))
            tabContainerBar.setBackgroundColor(Color.parseColor("#00A884"))
            tvEmptyState.setTextColor(Color.parseColor("#757575"))
            btnMainThemeToggle.text = "🌙 ডার্ক মোড"
            btnMainThemeToggle.setBackgroundColor(Color.parseColor("#00796B"))
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
                    else -> AppCompatDelegate
