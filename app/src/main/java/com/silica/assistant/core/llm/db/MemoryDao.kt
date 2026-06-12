package com.silica.assistant.core.llm.db

import androidx.room.*
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.llm.model.UserFactEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.core.llm.model.AchievementEntity
import com.silica.assistant.core.llm.model.FriendEntity
import com.silica.assistant.core.llm.model.SocialMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessagesFlow(limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<ChatMessageEntity>

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

    @Query("DELETE FROM user_facts")
    suspend fun deleteAllFacts()
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

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY category, tier ASC")
    fun getAllAchievements(): kotlinx.coroutines.flow.Flow<List<AchievementEntity>>

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

