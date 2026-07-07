package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.SocialMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialMessageDao {
    @Insert
    suspend fun insertMessage(message: SocialMessageEntity)

    @Query("SELECT * FROM social_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<SocialMessageEntity>>

    @Query("UPDATE social_messages SET isRead = 1 WHERE chatId = :chatId AND senderId != :currentUserId")
    suspend fun markAsRead(chatId: String, currentUserId: String)

    @Query("DELETE FROM social_messages WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)
}
