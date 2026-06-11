package com.silica.assistant.core.llm.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.llm.model.UserFactEntity

@Database(entities = [ChatMessageEntity::class, UserFactEntity::class], version = 1)
abstract class SilicaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userFactDao(): UserFactDao
}
