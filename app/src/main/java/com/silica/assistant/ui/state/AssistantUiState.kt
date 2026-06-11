package com.silica.assistant.ui.state

import com.silica.assistant.core.llm.model.UserProfileEntity

data class AssistantUiState(
    val commandText: String = "",
    val isListening: Boolean = false,
    val userProfile: UserProfileEntity? = null
)