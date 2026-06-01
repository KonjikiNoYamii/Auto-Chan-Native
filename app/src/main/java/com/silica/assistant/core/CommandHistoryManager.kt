package com.silica.assistant.core

import androidx.compose.runtime.mutableStateListOf

import com.silica.assistant.model.CommandLog

object CommandHistoryManager {

    val logs = mutableStateListOf<CommandLog>()

    fun add(command: String) {

        logs.add(
            0,
            CommandLog(
                text = command,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}