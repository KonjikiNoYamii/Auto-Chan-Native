package com.silica.assistant.core.config

object AssistantConfig {

    // apakah command harus memakai wake word
    var requireWakeWord = false

    // nama assistant
    var assistantName = "silica"

    // efek suara bubble
    var enableVoiceSound = true

    // bubble overlay
    var enableBubble = true

    // SSH: apakah user sudah pernah konfirmasi warning keamanan
    var sshWarningAcknowledged = false
}