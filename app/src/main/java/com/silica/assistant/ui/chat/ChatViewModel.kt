package com.silica.assistant.ui.chat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.R
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

    var creatingModel by mutableStateOf(false)
        private set

    var modelCreateResult by mutableStateOf<String?>(null)
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
                    error = e.message ?: "Gagal terhubung ke Ollama."
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

    fun createModel(context: Context) {
        if (creatingModel) return
        creatingModel = true
        modelCreateResult = null
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val content = context.resources.openRawResource(R.raw.modelfile_yami)
                        .bufferedReader().use { it.readText() }
                    val tmpFile = java.io.File(context.cacheDir, "Modelfile_Yami")
                    tmpFile.writeText(content)
                    val home = com.silica.assistant.core.ssh.SshManager.homePath.let {
                        if (it == "/") "/root" else it
                    }
                    val upload = com.silica.assistant.core.ssh.SshManager.uploadFile(
                        tmpFile.absolutePath, "$home/Modelfile_Yami"
                    )
                    tmpFile.delete()
                    if (upload.isFailure) return@withContext Result.failure<String>(upload.exceptionOrNull() ?: Exception("Upload gagal"))
                    com.silica.assistant.core.ssh.SshManager.executeCommand("ollama create yami -f $home/Modelfile_Yami")
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.onSuccess {
                LlmConfig.model = "yami"
                modelCreateResult = "Model Yami berhasil dibuat!"
            }.onFailure { e ->
                modelCreateResult = "Gagal: ${e.message}"
            }
            creatingModel = false
        }
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
