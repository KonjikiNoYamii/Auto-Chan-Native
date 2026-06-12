package com.silica.assistant.core.llm

import com.silica.assistant.BuildConfig
import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object LlmConfig : KoinComponent {
    private val userFactDao: UserFactDao by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    var geminiApiKey: String = BuildConfig.GEMINI_API_KEY
    var geminiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/"
    var geminiModel: String = "gemini-2.5-flash"
    var geminiTimeout: Int = 30000
    var useGeminiFallback: Boolean = true

    var localEndpoint: String = "https://truth-riveter-flier.ngrok-free.dev/v1/chat/completions"
    var localApiKey: String = ""
    var useLocalPrimary: Boolean = true

    var model: String = "llama3-8b-8192"
    var personalityPrompt: String = "Yami adalah alien assassin yang tenang dan sopan. Dia bicara dengan gaya elegan, sedikit stoik tapi penuh perhatian pada partnernya. Dia suka Taiyaki dan tidak suka hal yang tidak sopan (Harenchi). Responnya santai, natural, dan tidak kaku."

    fun init() {
        scope.launch {
            personalityPrompt = userFactDao.getFact("personality_prompt")?.value ?: personalityPrompt
        }
    }

    fun save() {
        scope.launch {
            userFactDao.insertFact(UserFactEntity("personality_prompt", personalityPrompt))
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        scope.launch {
            try {
                val authRepository: com.silica.assistant.core.auth.AuthRepository = org.koin.core.context.GlobalContext.get().get()
                if (authRepository.isLoggedIn()) {
                    authRepository.syncPush()
                }
            } catch (e: Exception) {
                // Koin might not be ready or repository not found
            }
        }
    }
}
