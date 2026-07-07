package com.silica.assistant.core.llm.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.llm.model.UserFactEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.core.llm.model.AchievementEntity
import com.silica.assistant.core.llm.model.FriendEntity
import com.silica.assistant.core.llm.model.SocialMessageEntity

@Database(entities = [ChatMessageEntity::class, UserFactEntity::class, UserProfileEntity::class, QuestEntity::class, AchievementEntity::class, FriendEntity::class, SocialMessageEntity::class], version = 13, exportSchema = false)
abstract class SilicaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userFactDao(): UserFactDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun questDao(): QuestDao
    abstract fun achievementDao(): AchievementDao
    abstract fun friendDao(): FriendDao
    abstract fun socialMessageDao(): SocialMessageDao
}

