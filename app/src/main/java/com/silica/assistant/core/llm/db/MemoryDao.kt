package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.llm.model.UserFactEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.core.llm.model.AchievementEntity
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

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0 LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET affinityPoints = affinityPoints + :points WHERE id = 0")
    suspend fun incrementAffinity(points: Int)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY category, tier ASC")
    fun getAllAchievements(): kotlinx.coroutines.flow.Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getAchievementById(id: String): AchievementEntity?

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun getCount(): Int
}

