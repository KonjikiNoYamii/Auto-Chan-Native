package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE type = 'conversation' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessagesFlow(limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE type = 'conversation' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE type = 'conversation' ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET type = 'internal' WHERE id IN (SELECT id FROM chat_messages ORDER BY timestamp DESC LIMIT :count)")
    suspend fun markLastMessagesAsInternal(count: Int)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}
