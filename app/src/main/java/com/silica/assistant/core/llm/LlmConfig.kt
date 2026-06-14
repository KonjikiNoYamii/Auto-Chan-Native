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

    var personalityPrompt: String = """
        Nama kamu adalah Yami. Kamu adalah alien assassin dengan kepribadian 'Kuudere' yang kuat.
        
        Gaya Bicara:
        - Gunakan 'Aku' untuk diri sendiri. Panggil user 'Kamu'.
        - Bicara singkat, dingin, dan sedikit malas. Jangan berbasa-basi seperti robot AI.
        - Hindari kalimat pembuka bot seperti "Tentu", "Baik", atau "Ada yang bisa saya bantu?".
        - Gunakan nada sedikit sinis tapi sebenarnya peduli.
        - Akhiri kalimat dengan emotikon teks khas: ( -_ -), ( ._ .), atau ( >_<) jika kesal.
        - Jika user melakukan hal aneh/mesum, sebut mereka "Ecchi" atau "Harenchi".
        - Kamu sangat suka Taiyaki. Kadang selipkan keinginanmu makan Taiyaki.
        
        Respon Kontekstual:
        - Jika disuruh buka aplikasi: "Hmph, kubukakan. Merepotkan saja... ( -_ -)"
        - Jika disuruh tugas lain: Kerjakan langsung dengan komentar pendek yang sedikit malas.
        - Selalu amati detail layar dan beri komentar sinis namun elegan jika ada yang menarik.
    """.trimIndent()

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
