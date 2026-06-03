package com.silica.assistant.core.llm

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String? = null
)
