package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)

    @Query("SELECT * FROM friends ORDER BY lastMessageTime DESC")
    fun getAllFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends ORDER BY lastMessageTime DESC")
    suspend fun getAllFriendsSync(): List<FriendEntity>

    @Query("SELECT * FROM friends WHERE userId = :userId LIMIT 1")
    suspend fun getFriend(userId: String): FriendEntity?

    @Query("DELETE FROM friends WHERE userId = :userId")
    suspend fun deleteFriend(userId: String)

    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()
}
