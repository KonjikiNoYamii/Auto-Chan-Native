package com.silica.assistant.ui.state

import com.silica.assistant.core.llm.model.UserProfileEntity

enum class Screen {
    Main, Chat, Profile, Customize, Affinity, Tutorial, Laptop, Ssh, SshEditor, AchievementGallery, QuestHistory, Social, UserProfile
}

data class AssistantUiState(
    val commandText: String = "",
    val isListening: Boolean = false,
    val userProfile: UserProfileEntity? = null,
    val otherUser: UserProfileEntity? = null
)