package com.example.unseenchat.ai

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiAiHelper {
    // এখানে ডাবল কোটেশনের ভেতরে আপনার কপি করা মূল API Key বসান
    private const val API_KEY = "এখানে_কপি_করা_KEY_পেস্ট_করুন"

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = API_KEY
        )
    }

    // ১. স্মার্ট সাজেশন (৩টি সংক্ষিপ্ত উত্তরের অপশন)
    suspend fun getSmartReplies(incomingText: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an assistant generating quick chat reply suggestions.
                Given the incoming message below, provide 3 short, natural reply options in the same language (Bengali or English, max 3-4 words each).
                Separate the 3 options with a vertical bar '|'. Do not include quotes, numbering, or extra text.
                
                Incoming message: "$incomingText"
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val text = response.text?.trim() ?: return@withContext emptyList()
            text.split("|")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
                .take(3)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ২. অটো রিপ্লাই জেনারেটর
    suspend fun getAutoReplyText(incomingText: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are an authentic personal chat assistant. Generate a single, natural, polite, and brief reply in the same language as the incoming message (Bengali or English).
                Do not include any quotes, markdown headers, or explanations.
                
                Incoming message: "$incomingText"
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            response.text?.trim()?.removeSurrounding("\"")
        } catch (e: Exception) {
            null
        }
    }
}
