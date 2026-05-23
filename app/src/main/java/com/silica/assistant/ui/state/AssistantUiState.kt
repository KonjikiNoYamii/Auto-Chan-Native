package com.silica.assistant.ui.state

data class AssistantUiState(
    val commandText: String = "",
    val isListening: Boolean = false
)