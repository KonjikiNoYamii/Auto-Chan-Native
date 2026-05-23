package com.silica.assistant.core.model

data class CommandResult(
    val command: String,
    val confidence: Int,
    val rawInput: String
)