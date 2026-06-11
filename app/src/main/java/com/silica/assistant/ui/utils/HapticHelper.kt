package com.silica.assistant.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

object HapticHelper {
    fun playClick(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // Short subtle vibration
    }

    fun playLongPress(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun playSuccess(haptic: HapticFeedback) {
        // Compose HapticFeedbackType is limited, so we use LongPress or repeat
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun playError(haptic: HapticFeedback) {
        // Simulating error with multiple pulses if possible, but standard is just LongPress
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedback = LocalHapticFeedback.current
