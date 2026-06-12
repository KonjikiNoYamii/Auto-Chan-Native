package com.silica.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.ui.MainScreen
import com.silica.assistant.ui.theme.SilicaTheme
import kotlinx.coroutines.launch
import com.silica.assistant.ui.state.Screen
import com.silica.assistant.core.llm.WaifuNotifier
import com.silica.assistant.core.system.SoundManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Pre-warm Voice System
        VoiceManager.init(this)
        SoundManager.init(this)
        
        // 2. Start Health Check for LLM
        lifecycleScope.launch {
            LlmClient.startPeriodicHealthCheck()
            WaifuNotifier.showTimeBasedGreeting()
        }

        val openScreen = intent.getStringExtra("OPEN_SCREEN")

        setContent { 
            SilicaTheme(darkTheme = false) { 
                MainScreen(initialScreen = if (openScreen == "CHAT") Screen.Chat else Screen.Main) 
            } 
        }
    }
}
