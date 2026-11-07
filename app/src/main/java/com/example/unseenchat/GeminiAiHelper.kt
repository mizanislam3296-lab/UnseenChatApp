package com.example.unseenchat.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiAiHelper {
    private const val API_KEY = "AIzaSyAY8f-ah3IOG_-Cnd6yUX_Qqcg_e85NpbY"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    // ১. স্মার্ট সাজেশন (৩টি সংক্ষিপ্ত উত্তরের অপশন)
    suspend fun getSmartReplies(incomingText: String): List<String> = withContext(Dispatchers.IO) {
        val prompt = """
            You are an assistant generating quick chat reply suggestions.
            Given the incoming message below, provide 3 short, natural reply options in the same language (Bengali or English, max 3-4 words each).
            Separate the 3 options with a vertical bar '|'. Do not include quotes, numbering, or extra text.
            
            Incoming message: "$incomingText"
        """.trimIndent()

        val response = callGeminiRestApi(prompt) ?: return@withContext emptyList()
        response.split("|")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    // ২. অটো রিপ্লাই জেনারেটর
    suspend fun getAutoReplyText(incomingText: String): String? = withContext(Dispatchers.IO) {
        val prompt = """
            You are an authentic personal chat assistant. Generate a single, natural, polite, and brief reply in the same language as the incoming message (Bengali or English).
            Do not include any quotes, markdown headers, or explanations.
            
            Incoming message: "$incomingText"
        """.trimIndent()

        callGeminiRestApi(prompt)?.removeSurrounding("\"")
    }

    // কোনো বাহ্যিক লাইব্রেরি ছাড়া সরাসরি জেমিনি REST API কল
    private fun callGeminiRestApi(prompt: String): String? {
        return try {
            val url = URL("$BASE_URL?key=$API_KEY")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            // Request JSON তৈরি
            val textPart = JSONObject().put("text", prompt)
            val parts = JSONArray().put(textPart)
            val contentObj = JSONObject().put("parts", parts)
            val contents = JSONArray().put(contentObj)
            val requestJson = JSONObject().put("contents", contents)

            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(requestJson.toString())
                os.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                // Response JSON থেকে উত্তর বের করা
                val root = JSONObject(sb.toString())
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content") ?: return null
                    val responseParts = content.optJSONArray("parts") ?: return null
                    if (responseParts.length() > 0) {
                        return responseParts.getJSONObject(0).optString("text", "").trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
