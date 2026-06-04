package com.silica.assistant.ui.chat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.core.llm.ChatMessage
import com.silica.assistant.core.llm.EmotionMapper
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.llm.LlmConfig
import com.silica.assistant.core.llm.MemoryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var memories by mutableStateOf<List<String>>(emptyList())
        private set

    fun sendMessage(context: Context, text: String) {
        if (text.isBlank()) return

        // handle memory management commands
        val forgetCmd = MemoryManager.extractForgetCommand(text)
        if (forgetCmd != null) {
            MemoryManager.removeMemory(context, forgetCmd)
            memories = MemoryManager.getMemories(context)
            messages = messages + ChatMessage(role = "user", content = text)
            messages = messages + ChatMessage(role = "assistant", content = "...Baik, aku lupakan itu.")
            return
        }

        if (MemoryManager.isClearCommand(text)) {
            MemoryManager.clearAll(context)
            memories = emptyList()
            messages = messages + ChatMessage(role = "user", content = text)
            messages = messages + ChatMessage(role = "assistant", content = "...Semua ingatan dihapus.")
            return
        }

        if (MemoryManager.isListCommand(text)) {
            val mems = MemoryManager.getMemories(context)
            val content = if (mems.isEmpty()) {
                "...Aku belum tahu banyak tentangmu."
            } else {
                "Yang aku tahu:\n" + mems.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n")
            }
            messages = messages + ChatMessage(role = "user", content = text)
            messages = messages + ChatMessage(role = "assistant", content = content)
            return
        }

        // auto-store detected facts silently
        val newFacts = MemoryManager.autoExtract(text)
        for (fact in newFacts) {
            MemoryManager.addMemory(context, fact)
        }
        if (newFacts.isNotEmpty()) {
            memories = MemoryManager.getMemories(context)
        }

        // send to LLM with memory context
        val userMsg = ChatMessage(role = "user", content = text)
        messages = messages + userMsg
        isLoading = true
        error = null
        val memoryCtx = MemoryManager.buildContext(context)

        viewModelScope.launch {
            LlmClient.chat(messages, memoryContext = memoryCtx)
                .onSuccess { reply ->
                    val bubbles = splitResponse(reply.content)
                    for (i in bubbles.indices) {
                        val (cleanText, emotion) = EmotionMapper.parseEmotion(bubbles[i])
                        val e = if (i == 0) emotion else null
                        messages = messages + ChatMessage(role = "assistant", content = cleanText, emotion = e)
                        delay(600)
                    }
                }
                .onFailure { e ->
                    error = e.message ?: "Gagal terhubung ke AI."
                }
            isLoading = false
        }
    }

    private fun splitResponse(text: String): List<String> {
        val parts = text.trim().split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
        if (parts.size <= 1) return listOf(text.trim())
        return parts.take(4)
    }

    fun loadMemories(context: Context) {
        memories = MemoryManager.getMemories(context)
    }

    fun deleteMemory(context: Context, index: Int) {
        runCatching { MemoryManager.removeMemoryAt(context, index) }
        memories = MemoryManager.getMemories(context)
    }

    fun clearChat() {
        messages = emptyList()
        error = null
    }
}
