package com.silica.assistant.core.llm.model

import kotlinx.serialization.Serializable
import com.silica.assistant.core.llm.ChatMessage

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val images: List<String>? = null
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null,
    val delta: ChatDelta? = null,
    val index: Int,
    val finish_reason: String? = null
)

@Serializable
data class ChatDelta(
    val content: String? = null
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice>,
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
