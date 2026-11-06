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
        Index(value = ["conversationKey", "senderName", "messageText", "timestamp"], unique = true)
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationKey: String,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isOutgoing: Boolean = false,
    val mediaType: String = MediaType.TEXT,
    val localMediaPath: String? = null
)

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE conversationKey = :convKey ORDER BY timestamp ASC")
    fun getMessagesByConversation(convKey: String): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT COUNT(*) FROM chat_messages 
        WHERE conversationKey = :convKey 
        AND messageText = :text 
        AND ABS(timestamp - :time) < 2000
    """)
    suspend fun isDuplicateMessage(convKey: String, text: String, time: Long): Int

    @Query("""
        UPDATE chat_messages 
        SET localMediaPath = :filePath, mediaType = 'VOICE'
        WHERE id = (
            SELECT id FROM chat_messages 
            WHERE packageName = :pkgName AND mediaType = 'VOICE' AND localMediaPath IS NULL 
            ORDER BY timestamp DESC LIMIT 1
        )
    """)
    suspend fun attachVoiceFileToLatestPendingMessage(pkgName: String, filePath: String)
}

@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unseen_chat_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
