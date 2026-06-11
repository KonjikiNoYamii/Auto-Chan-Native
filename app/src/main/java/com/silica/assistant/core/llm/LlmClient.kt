package com.silica.assistant.core.llm

import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object LlmClient : KoinComponent {
    private val repository: LlmRepository by inject()

    val activeProvider: String
        get() = repository.activeProvider

    suspend fun startPeriodicHealthCheck() {
        repository.startPeriodicHealthCheck()
    }

    suspend fun chat(messages: List<ChatMessage>, memoryContext: String = ""): Result<ChatMessage> {
        return repository.chat(messages, memoryContext)
    }

    suspend fun chatStream(
        messages: List<ChatMessage>,
        memoryContext: String = "",
        onToken: (String) -> Unit
    ): Result<ChatMessage> {
        val fullContent = StringBuilder()
        try {
            repository.chatStream(messages, memoryContext).collect { token ->
                fullContent.append(token)
                onToken(token)
            }
            return Result.success(ChatMessage(role = "assistant", content = fullContent.toString()))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun generateActivityComment(appName: String, isGame: Boolean, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        if (onToken != null) {
            val fullContent = StringBuilder()
            repository.chatStream(listOf(ChatMessage("user", "User buka $appName. Beri komentar singkat.")), "").collect { token ->
                fullContent.append(token)
                onToken(token)
            }
            return fullContent.toString()
        }
        return repository.generateActivityComment(appName, isGame, contextHint)
    }

    suspend fun generateScreenComment(appName: String, uiText: String, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        // For simplicity, using non-streaming for now or adapting as needed
        return repository.chat(listOf(ChatMessage("user", "Konteks: $appName. Layar: $uiText. Beri reaksi natural."))).getOrNull()?.content
    }

    suspend fun describeScreen(appName: String, uiText: String, screenshotJpeg: ByteArray?, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        return repository.describeScreen(appName, uiText, screenshotJpeg, contextHint)
    }

    suspend fun generateTaskPlan(userCommand: String): String? {
        return repository.generateTaskPlan(userCommand)
    }

    suspend fun executeAiTask(userCommand: String): String? {
        return repository.executeAiTask(userCommand)
    }

    suspend fun classifyQuestDifficulty(questTitle: String): String? {
        return repository.classifyQuestDifficulty(questTitle)
    }
}

