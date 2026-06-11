package com.silica.assistant.core.llm.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "chat_messages")
@Serializable
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String? = null
)

@Entity(tableName = "user_facts")
data class UserFactEntity(
    @PrimaryKey val key: String = "",
    val value: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 0,
    val userName: String = "User",
    val userNickname: String? = null,
    val affinityPoints: Int = 0, // Legacy support, but we'll use XP mostly
    val level: Int = 1,
    val xp: Int = 0,
    val mood: Float = 1.0f, // 0.0 to 1.0
    val stamina: Float = 1.0f, // 0.0 to 1.0
    val relationshipRoute: String = "NONE", // NONE, FRIEND, LOVER
    val dailyGiftCount: Int = 0,
    val lastGiftDate: String = "", // Format: YYYY-MM-DD
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastQuestCompletionDate: String = "", // Format: YYYY-MM-DD
    val lastInteractionTime: Long = System.currentTimeMillis(),
    val preferredMusicGenre: String? = null,
    val moodState: String = "NEUTRAL",
    val aiName: String = "silica",
    val inventory: String = "", // Comma-separated items: "Taiyaki,Cokelat"
    val customNicknames: String = "" // JSON or Comma-separated: "FRIEND:Dan,LOVER:Sayang"
)

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val category: String,
    val title: String,
    val description: String,
    val tier: Int, // 1 to 10
    val targetValue: Int,
    var currentValue: Int = 0,
    var isUnlocked: Boolean = false,
    var unlockedAt: Long? = null
)

