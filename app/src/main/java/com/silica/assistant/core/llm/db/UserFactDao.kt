package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: UserFactEntity)

    @Query("SELECT * FROM user_facts")
    fun getAllFacts(): Flow<List<UserFactEntity>>

    @Query("SELECT * FROM user_facts WHERE `key` = :key LIMIT 1")
    suspend fun getFact(key: String): UserFactEntity?

    @Query("DELETE FROM user_facts")
    suspend fun deleteAllFacts()
}
