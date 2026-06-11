package com.silica.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.ui.MainScreen
import com.silica.assistant.ui.theme.SilicaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Pre-warm Voice System
        VoiceManager.init(this)
        com.silica.assistant.core.system.SoundManager.init(this)
        
        // 2. Start Health Check for LLM
        lifecycleScope.launch {
            LlmClient.startPeriodicHealthCheck()
        }

        checkWriteSettingsPermission()

        setContent { SilicaTheme(darkTheme = false) { MainScreen() } }
    }

    private fun checkWriteSettingsPermission() {

        if (!Settings.System.canWrite(this)) {

            val intent =
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))

            startActivity(intent)
        }
    }
}
