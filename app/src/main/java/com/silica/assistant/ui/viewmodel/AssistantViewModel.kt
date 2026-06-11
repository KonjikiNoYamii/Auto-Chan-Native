package com.silica.assistant.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.ui.state.AssistantUiState
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssistantViewModel : ViewModel(), KoinComponent {

    private val userProfileDao: UserProfileDao by inject()

    var uiState by mutableStateOf(AssistantUiState())
        private set

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val profile = userProfileDao.getProfile()
            uiState = uiState.copy(userProfile = profile)
        }
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