package com.silica.assistant.core.llm

import com.silica.assistant.BuildConfig

object LlmConfig {
    var endpoint: String = "https://openrouter.ai/api/v1/chat/completions"
    var geminiEndpoint: String = "https://truth-riveter-flier.ngrok-free.dev/v1/chat/completions"
    var model: String = "openrouter/free"
    var language: String = "Bahasa Indonesia"
    var apiKey: String = BuildConfig.OPENROUTER_API_KEY
    var geminiSecret: String = ""
    var useGeminiFallback: Boolean = true
    var geminiTimeout: Int = 30000
    var personalityPrompt: String = "Khas Yami: assassin dingin, sangat tenang, sopan namun blak-blakan. Tidak menyukai hal tidak senonoh (Harenchi), namun hanya mengatakannya jika benar-benar perlu. Jangan mengulang kata 'mesum' terlalu sering."
}
