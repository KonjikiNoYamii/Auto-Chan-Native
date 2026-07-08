package com.silica.assistant.core.llm.model

import kotlinx.serialization.Serializable
import com.silica.assistant.core.llm.ChatMessage

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val images: List<String>? = null
)

// --- Native Gemini Models ---
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String>? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: ApiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null
)
// --- End Native Gemini Models ---

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
