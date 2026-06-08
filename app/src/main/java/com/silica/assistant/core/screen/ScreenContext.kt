package com.silica.assistant.core.screen

import android.graphics.Bitmap

data class ScreenContext(
    val appName: String,
    val uiText: String,
    val screenshot: Bitmap?,
    val timestamp: Long = System.currentTimeMillis()
)
