package com.silica.assistant.core.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val userId: String? = null
)

@Serializable
data class SyncResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class RemoteProfile(
    val userName: String,
    val userNickname: String?,
    val level: Int,
    val xp: Int,
    val mood: Float,
    val stamina: Float,
    val relationshipRoute: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastQuestCompletionDate: String
)
