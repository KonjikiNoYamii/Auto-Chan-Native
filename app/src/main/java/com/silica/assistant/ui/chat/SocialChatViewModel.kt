package com.silica.assistant.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.core.auth.SocialRepository
import com.silica.assistant.core.llm.db.SocialMessageDao
import com.silica.assistant.core.llm.model.SocialMessageEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SocialChatViewModel(
    private val otherUserId: String,
    private val socialRepository: SocialRepository,
    private val socialMessageDao: SocialMessageDao
) : ViewModel() {

    var messages by mutableStateOf<List<SocialMessageEntity>>(emptyList())
        private set

    var inputText by mutableStateOf("")

    init {
        viewModelScope.launch {
            observeRemoteMessages()
        }
        viewModelScope.launch {
            observeLocalMessages()
        }
    }

    private suspend fun observeRemoteMessages() {
        socialRepository.observeMessages(otherUserId).collectLatest { remoteMessages ->
            remoteMessages.forEach { msg ->
                socialMessageDao.insertMessage(msg)
            }
        }
    }

    private suspend fun observeLocalMessages() {
        val currentUserId = socialRepository.getCurrentUserId() ?: return
        val chatId = if (currentUserId.compareTo(otherUserId) < 0) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        socialMessageDao.getMessagesForChat(chatId).collectLatest {
            messages = it
        }
    }

    fun sendMessage() {
        if (inputText.isBlank()) return
        val content = inputText
        inputText = ""
        viewModelScope.launch {
            socialRepository.sendMessage(otherUserId, content)
        }
    }
}
