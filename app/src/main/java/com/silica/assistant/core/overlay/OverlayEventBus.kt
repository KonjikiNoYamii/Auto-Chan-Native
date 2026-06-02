package com.silica.assistant.core.overlay

import androidx.compose.runtime.mutableStateOf

object OverlayEventBus {

    var onBubble: ((String) -> Unit)? = null
    val navigateScreen = mutableStateOf<String?>(null)

    fun send(text: String) {
        onBubble?.invoke(text)
    }
}