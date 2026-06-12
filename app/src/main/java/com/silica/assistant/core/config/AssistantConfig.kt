package com.silica.assistant.core.config

import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AssistantConfig : KoinComponent {
    private val userFactDao: UserFactDao by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Default values
    var requireWakeWord = false
    var assistantName = "silica"
    var customGreeting = ""
    var enableVoiceSound = true
    var enableBubble = true
    var sshWarningAcknowledged = false
    var overlaySizeDefault = 120
    var overlaySizeGameMode = 80

    fun init() {
        scope.launch {
            requireWakeWord = getBool("require_wake_word", false)
            assistantName = getString("assistant_name", "silica")
            customGreeting = getString("custom_greeting", "")
            enableVoiceSound = getBool("enable_voice_sound", true)
            enableBubble = getBool("enable_bubble", true)
            sshWarningAcknowledged = getBool("ssh_warning_acknowledged", false)
            overlaySizeDefault = getInt("overlay_size_default", 120)
            overlaySizeGameMode = getInt("overlay_size_game_mode", 80)
        }
    }

    private suspend fun getString(key: String, default: String): String {
        return userFactDao.getFact(key)?.value ?: default
    }

    private suspend fun getBool(key: String, default: Boolean): Boolean {
        return userFactDao.getFact(key)?.value?.toBoolean() ?: default
    }

    private suspend fun getInt(key: String, default: Int): Int {
        return userFactDao.getFact(key)?.value?.toIntOrNull() ?: default
    }

    fun save() {
        scope.launch {
            userFactDao.insertFact(UserFactEntity("require_wake_word", requireWakeWord.toString()))
            userFactDao.insertFact(UserFactEntity("assistant_name", assistantName))
            userFactDao.insertFact(UserFactEntity("custom_greeting", customGreeting))
            userFactDao.insertFact(UserFactEntity("enable_voice_sound", enableVoiceSound.toString()))
            userFactDao.insertFact(UserFactEntity("enable_bubble", enableBubble.toString()))
            userFactDao.insertFact(UserFactEntity("ssh_warning_acknowledged", sshWarningAcknowledged.toString()))
            userFactDao.insertFact(UserFactEntity("overlay_size_default", overlaySizeDefault.toString()))
            userFactDao.insertFact(UserFactEntity("overlay_size_game_mode", overlaySizeGameMode.toString()))
            
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        scope.launch {
            try {
                val authRepository: com.silica.assistant.core.auth.AuthRepository = org.koin.core.context.GlobalContext.get().get()
                if (authRepository.isLoggedIn()) {
                    val result = authRepository.syncPush()
                    if (result.isFailure) {
                        android.util.Log.e("AssistantConfig", "Sync failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AssistantConfig", "Sync trigger error", e)
            }
        }
    }
}
