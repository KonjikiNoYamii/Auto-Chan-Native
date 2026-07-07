package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.QuestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Insert
    suspend fun insertQuest(quest: QuestEntity)

    @Update
    suspend fun updateQuest(quest: QuestEntity)

    @Query("SELECT * FROM quests ORDER BY createdAt DESC")
    suspend fun getAllQuestsSync(): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getActiveQuests(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE title LIKE '%' || :query || '%' AND isCompleted = 0 LIMIT 1")
    suspend fun findActiveQuestByTitle(query: String): QuestEntity?

    @Query("SELECT * FROM quests WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedQuests(): Flow<List<QuestEntity>>

    @Query("SELECT COUNT(*) FROM quests WHERE isCompleted = 1 AND completedAt >= :startTime")
    suspend fun getCompletedCountSince(startTime: Long): Int

    @Query("DELETE FROM quests")
    suspend fun deleteAllQuests()

    @Delete
    suspend fun deleteQuest(quest: QuestEntity)
}
