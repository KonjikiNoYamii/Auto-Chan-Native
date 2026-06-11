package com.silica.assistant.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.core.llm.db.QuestDao
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.ui.state.AssistantUiState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssistantViewModel : ViewModel(), KoinComponent {

    private val userProfileDao: UserProfileDao by inject()
    private val moodManager: MoodManager by inject()
    private val questDao: QuestDao by inject()
    private val achievementDao: com.silica.assistant.core.llm.db.AchievementDao by inject()

    var uiState by mutableStateOf(AssistantUiState())
        private set

    var inventory by mutableStateOf<List<String>>(emptyList())
        private set

    var activeQuests by mutableStateOf<List<QuestEntity>>(emptyList())
        private set

    var completedQuests by mutableStateOf<List<QuestEntity>>(emptyList())
        private set

    var achievements by mutableStateOf<List<com.silica.assistant.core.llm.model.AchievementEntity>>(emptyList())
        private set

    init {
        loadProfile()
        loadInventory()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            questDao.getActiveQuests().collect { activeQuests = it }
        }
        viewModelScope.launch {
            questDao.getCompletedQuests().collect { completedQuests = it }
        }
        viewModelScope.launch {
            achievementDao.getAllAchievements().collect { achievements = it }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val profile = userProfileDao.getProfile()
            uiState = uiState.copy(userProfile = profile)
        }
    }

    fun loadInventory() {
        viewModelScope.launch {
            inventory = moodManager.getInventory()
        }
    }

    fun addQuest(title: String, difficulty: String) {
        viewModelScope.launch {
            moodManager.addQuest(title, difficulty)
        }
    }

    fun completeQuest(title: String) {
        viewModelScope.launch {
            val result = moodManager.completeQuest(title)
            com.silica.assistant.core.overlay.OverlayEventBus.onBubble?.invoke(result)
            loadProfile()
            loadInventory()
        }
    }

    fun deleteQuest(quest: QuestEntity) {
        viewModelScope.launch {
            questDao.deleteQuest(quest)
        }
    }

    suspend fun classifyQuest(title: String): String {
        return com.silica.assistant.core.llm.LlmClient.classifyQuestDifficulty(title) ?: "MEDIUM"
    }

    fun updateCommandText(text: String) {
        uiState = uiState.copy(commandText = text)
    }

    fun clearCommand() {
        uiState = uiState.copy(commandText = "")
    }

    fun setListening(listening: Boolean) {
        uiState = uiState.copy(isListening = listening)
    }
}
