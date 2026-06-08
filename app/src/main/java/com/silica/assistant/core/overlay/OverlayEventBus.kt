package com.silica.assistant.core.overlay

import androidx.compose.runtime.mutableStateOf
import com.silica.assistant.service.SilicaAccessibilityService

object OverlayEventBus {

    var onBubble: ((String) -> Unit)? = null
    val navigateScreen = mutableStateOf<String?>(null)
    var gameModeRequest: Boolean? = null

    var accessibilityService: SilicaAccessibilityService? = null
    var screenCaptureCallback: (() -> Unit)? = null

    fun send(text: String) {
        onBubble?.invoke(text)
    }
}