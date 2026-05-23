package com.silica.assistant.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.silica.assistant.ui.state.AssistantUiState
class AssistantViewModel : ViewModel() {

    var uiState by mutableStateOf(AssistantUiState())
        private set

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