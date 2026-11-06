package com.example.unseenchat.data.cache

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.LruCache
import androidx.core.app.RemoteInput

data class ReplyActionPayload(
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>,
    val capturedAtMillis: Long = System.currentTimeMillis()
)

sealed class ReplyResult {
    data object Success : ReplyResult()
    data object SessionExpired : ReplyResult()
    data object ActionNotFound : ReplyResult()
    data class Error(val message: String) : ReplyResult()
}

object ReplyActionCache {
    private const val MAX_CACHE_ENTRIES = 50

    private val actionCache = object : LruCache<String, ReplyActionPayload>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: ReplyActionPayload): Int = 1
    }

    @Synchronized
    fun putReplyAction(conversationKey: String, payload: ReplyActionPayload) {
        actionCache.put(conversationKey, payload)
    }

    @Synchronized
    fun getReplyAction(conversationKey: String): ReplyActionPayload? {
        return actionCache.get(conversationKey)
    }

    @Synchronized
    fun removeReplyAction(conversationKey: String) {
        actionCache.remove(conversationKey)
    }

    fun executeDirectReply(
        context: Context,
        conversationKey: String,
        replyText: String
    ): ReplyResult {
        val payload = getReplyAction(conversationKey) ?: return ReplyResult.ActionNotFound

        return try {
            val localIntent = Intent()
            val resultsBundle = Bundle()

            for (remoteInput in payload.remoteInputs) {
                resultsBundle.putCharSequence(remoteInput.resultKey, replyText)
            }

            RemoteInput.addResultsToIntent(payload.remoteInputs, localIntent, resultsBundle)
            payload.pendingIntent.send(context.applicationContext, 0, localIntent)
            ReplyResult.Success
        } catch (e: PendingIntent.CanceledException) {
            removeReplyAction(conversationKey)
            ReplyResult.SessionExpired
        } catch (e: Exception) {
            ReplyResult.Error(e.localizedMessage ?: "রিপ্লাই পাঠানো সম্ভব হয়নি")
        }
    }
}
