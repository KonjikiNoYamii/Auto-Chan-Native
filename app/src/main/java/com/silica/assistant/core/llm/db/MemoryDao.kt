package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface UserFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: UserFactEntity)

    @Query("SELECT * FROM user_facts")
    fun getAllFacts(): Flow<List<UserFactEntity>>

    @Query("SELECT * FROM user_facts WHERE `key` = :key LIMIT 1")
    suspend fun getFact(key: String): UserFactEntity?
}
