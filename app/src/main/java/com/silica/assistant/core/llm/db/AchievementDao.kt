package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY category, tier ASC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements ORDER BY category, tier ASC")
    suspend fun getAllAchievementsSync(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getAchievementById(id: String): AchievementEntity?

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun getCount(): Int

    @Query("DELETE FROM achievements")
    suspend fun deleteAllAchievements()
}
