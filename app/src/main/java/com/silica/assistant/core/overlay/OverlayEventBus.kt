package com.silica.assistant.core.overlay

object OverlayEventBus {

    var onBubble: ((String) -> Unit)? = null

    fun send(text: String) {
        onBubble?.invoke(text)
    }
}