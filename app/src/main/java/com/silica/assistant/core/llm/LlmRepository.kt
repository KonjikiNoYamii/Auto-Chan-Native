package com.silica.assistant.core.llm

import kotlinx.coroutines.flow.Flow

interface LlmRepository {
    var activeProvider: String
    
    suspend fun startPeriodicHealthCheck()
    
    suspend fun chat(
        messages: List<ChatMessage>,
        memoryContext: String = ""
    ): Result<ChatMessage>
    
    fun chatStream(
        messages: List<ChatMessage>,
        memoryContext: String = ""
    ): Flow<String>
    
    suspend fun generateActivityComment(
        appName: String,
        isGame: Boolean,
        contextHint: String? = null
    ): String?

    suspend fun describeScreen(
        appName: String,
        uiText: String,
        screenshotJpeg: ByteArray?,
        contextHint: String? = null
    ): String?

    suspend fun generateTaskPlan(userCommand: String): String?
    
    suspend fun executeAiTask(userCommand: String): String?

    suspend fun classifyQuestDifficulty(questTitle: String): String?
}
