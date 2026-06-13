package com.silica.assistant.core.llm

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String? = null,
    val isTyping: Boolean = false,
    var displayedContent: String = content,
    val imageBase64: String? = null
)
