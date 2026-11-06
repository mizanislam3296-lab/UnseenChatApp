package com.example.unseenchat.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

object MediaType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
    const val VOICE = "VOICE"
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["conversationKey", "timestamp"]),
        Index(value = ["conversationKey", "senderName", "messageText", "timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationKey: String,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isOutgoing: Boolean = false,
    val mediaType: String = MediaType.TEXT,
    val localMediaPath: String? = null
)

// প্ল্যাটফর্ম অনুযায়ী চ্যাট তালিকার সামারি মডেল
data class ConversationSummary(
    val conversationKey: String,
    val packageName: String,
    val senderName: String,
    val lastMessage: String,
    val timestamp: Long
)

@Dao
interface ChatDao {

    // প্ল্যাটফর্ম (Package Name) অনুযায়ী আলাদা আলাদা ব্যক্তির চ্যাট তালিকা
    @Query("""
        SELECT conversationKey, packageName, senderName, 
               messageText AS lastMessage, 
               MAX(timestamp) AS timestamp 
        FROM chat_messages 
        WHERE packageName = :packageName 
        GROUP BY conversationKey 
        ORDER BY timestamp DESC
    """)
    fun getConversationsByPackage(packageName: String): Flow<List<ConversationSummary>>

    // নির্দিষ্ট ব্যক্তির সব মেসেজ
    @Query("SELECT * FROM chat_messages WHERE conversationKey = :convKey ORDER BY timestamp ASC")
    fun getMessagesByConversation(convKey: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationKey = :conversationKey ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationKey: String): Flow<List<ChatMessageEntity>>

    // সব মেসেজ পাওয়ার কুয়েরি
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    // ডুপ্লিকেট মেসেজ যাচাই
    @Query("""
        SELECT COUNT(*) FROM chat_messages 
        WHERE conversationKey = :convKey 
        AND messageText = :text 
        AND ABS(timestamp - :time) < 2000
    """)
    suspend fun isDuplicateMessage(convKey: String, text: String, time: Long): Boolean

    // ভয়েস মেসেজ আপডেট কুয়েরি
    @Query("""
        UPDATE chat_messages 
        SET localMediaPath = :filePath, mediaType = 'VOICE' 
        WHERE id = (
            SELECT id FROM chat_messages 
            WHERE packageName = :pkgName AND mediaType = 'VOICE' 
            ORDER BY timestamp DESC LIMIT 1
        )
    """)
    suspend fun attachVoiceFileToLatestPendingMessage(pkgName: String, filePath: String): Int

    @Query("DELETE FROM chat_messages WHERE conversationKey = :conversationKey")
    suspend fun deleteConversation(conversationKey: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

// ডাটাবেস ভার্সন ২ সেট করা হয়েছে
@Database(entities = [ChatMessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unseen_chat_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
