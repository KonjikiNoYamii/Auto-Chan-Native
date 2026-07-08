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
import com.silica.assistant.core.llm.MemoryManager
import com.silica.assistant.core.llm.db.ChatDao
import com.silica.assistant.core.llm.model.ChatMessageEntity
import com.silica.assistant.core.system.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.silica.assistant.core.llm.MoodManager

class ChatViewModel(
    private val chatDao: ChatDao,
    private val moodManager: MoodManager
) : ViewModel() {

    var inventory by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            LlmClient.startPeriodicHealthCheck()
            observeChatHistory()
            loadInventory()
        }
    }

    fun loadInventory() {
        viewModelScope.launch {
            inventory = moodManager.getInventory()
        }
    }

    fun giveGift(context: Context, item: String) {
        viewModelScope.launch {
            val (success, response) = moodManager.giveGift(item)
            if (success) {
                moodManager.removeItemFromInventory(item)
                loadInventory()
                
                // Add message to UI and DB
                val uMsg = ChatMessage(role = "user", content = "Memberi hadiah: $item")
                val aMsg = ChatMessage(role = "assistant", content = response)
                messages = messages + uMsg + aMsg
                saveMessage("user", uMsg.content)
                saveMessage("assistant", aMsg.content)
            } else {
                // Show refusal as AI message
                val aMsg = ChatMessage(role = "assistant", content = response)
                messages = messages + aMsg
                saveMessage("assistant", aMsg.content)
            }
        }
    }

    private suspend fun observeChatHistory() {
        chatDao.getRecentMessagesFlow(50).collectLatest { history ->
            val reversedHistory = history.reversed()
            
            // Only update if the size changed or last message changed to avoid unnecessary UI flickering during typewriter
            // Actually, typewriter effect modifies the 'messages' state directly. 
            // If we observe DB, we might overwrite the typewriter state.
            
            // To handle typewriter correctly, we should probably only update if the DB has NEW messages
            // that are NOT currently in our 'messages' list (excluding typing ones).
            
            val currentNonTypingMessages = messages.filter { !it.isTyping }
            if (reversedHistory.size != currentNonTypingMessages.size || (reversedHistory.isNotEmpty() && reversedHistory.last().content != currentNonTypingMessages.lastOrNull()?.content)) {
                 messages = reversedHistory.map { entity ->
                    ChatMessage(
                        role = entity.role,
                        content = entity.content,
                        timestamp = entity.timestamp,
                        emotion = entity.emotion,
                        isTyping = false,
                        displayedContent = entity.content
                    )
                }
            }
        }
    }

    private fun saveMessage(role: String, content: String, emotion: String? = null) {
        viewModelScope.launch {
            chatDao.insertMessage(
                ChatMessageEntity(
                    role = role,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    emotion = emotion
                )
            )
        }
    }

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
            val uMsg = ChatMessage(role = "user", content = text)
            val aMsg = ChatMessage(role = "assistant", content = "...Baik, aku lupakan itu.")
            messages = messages + uMsg + aMsg
            saveMessage("user", text)
            saveMessage("assistant", aMsg.content)
            viewModelScope.launch {
                MemoryManager.removeMemory(forgetCmd)
                memories = MemoryManager.getMemories()
            }
            return
        }

        if (MemoryManager.isClearCommand(text)) {
            val uMsg = ChatMessage(role = "user", content = text)
            val aMsg = ChatMessage(role = "assistant", content = "...Semua ingatan dihapus.")
            messages = messages + uMsg + aMsg
            saveMessage("user", text)
            saveMessage("assistant", aMsg.content)
            viewModelScope.launch {
                MemoryManager.clearAll()
            }
            memories = emptyList()
            return
        }

        if (MemoryManager.isListCommand(text)) {
            viewModelScope.launch {
                val mems = MemoryManager.getMemories()
                val content = if (mems.isEmpty()) {
                    "...Aku belum tahu banyak tentangmu."
                } else {
                    "Yang aku tahu:\n" + mems.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n")
                }
                val uMsg = ChatMessage(role = "user", content = text)
                val aMsg = ChatMessage(role = "assistant", content = content)
                messages = messages + uMsg + aMsg
                saveMessage("user", text)
                saveMessage("assistant", content)
            }
            return
        }

        // auto-store detected facts silently using AI
        viewModelScope.launch {
            MemoryManager.aiAutoExtract(userInput = text)
            memories = MemoryManager.getMemories()
        }

        // small affinity for each chat interaction
        moodManager.addAffinity(1)

        // send to LLM with memory context
        val userMsg = ChatMessage(role = "user", content = text)
        messages = messages + userMsg
        saveMessage("user", text)
        
        isLoading = true
        error = null

        viewModelScope.launch {
            val memoryCtx = MemoryManager.buildContext()
            LlmClient.chat(messages, memoryContext = memoryCtx)
                .onSuccess { reply ->
                    val bubbles = splitResponse(reply.content)
                    for (i in bubbles.indices) {
                        val (cleanText, emotion) = EmotionMapper.parseEmotion(bubbles[i])
                        val e = if (i == 0) emotion else null
                        
                        val newMessage = ChatMessage(
                            role = "assistant", 
                            content = cleanText, 
                            emotion = e,
                            isTyping = true,
                            displayedContent = ""
                        )
                        messages = messages + newMessage
                        saveMessage("assistant", cleanText, e)
                        
                        // Typewriter effect
                        val messageIndex = messages.size - 1
                        SoundManager.playSound("pop")
                        for (charIndex in cleanText.indices) {
                            delay(30) // speed of typing
                            val updatedMessages = messages.toMutableList()
                            val msg = updatedMessages[messageIndex]
                            updatedMessages[messageIndex] = msg.copy(
                                displayedContent = cleanText.take(charIndex + 1)
                            )
                            messages = updatedMessages
                        }
                        
                        // Finalize typing
                        val finalMessages = messages.toMutableList()
                        finalMessages[messageIndex] = finalMessages[messageIndex].copy(isTyping = false)
                        messages = finalMessages
                        
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
        viewModelScope.launch {
            memories = MemoryManager.getMemories()
        }
    }

    fun deleteMemory(context: Context, index: Int) {
        viewModelScope.launch {
            MemoryManager.removeMemoryAt(index)
            memories = MemoryManager.getMemories()
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            MemoryManager.clearAll()
            memories = MemoryManager.getMemories()
        }
    }

    fun clearChat() {
        messages = emptyList()
        error = null
        viewModelScope.launch {
            chatDao.clearHistory()
        }
    }
}
