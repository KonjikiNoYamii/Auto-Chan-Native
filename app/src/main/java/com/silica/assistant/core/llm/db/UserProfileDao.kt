package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.UserProfileEntity

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0 LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET affinityPoints = affinityPoints + :points WHERE id = 0")
    suspend fun incrementAffinity(points: Int)
}
