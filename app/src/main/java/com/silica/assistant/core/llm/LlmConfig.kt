package com.silica.assistant.core.llm

import com.silica.assistant.BuildConfig

object LlmConfig {
    var geminiApiKey: String = BuildConfig.GEMINI_API_KEY
    var geminiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/"
    var geminiModel: String = "gemini-2.5-flash"
    var geminiTimeout: Int = 30000
    var useGeminiFallback: Boolean = true

    var localEndpoint: String = "https://truth-riveter-flier.ngrok-free.dev/v1/chat/completions"
    var localApiKey: String = ""
    var useLocalPrimary: Boolean = true

    var endpoint: String = "https://api.groq.com/openai/v1/chat/completions"
    var model: String = "llama3-8b-8192"
    var language: String = "Bahasa Indonesia"
    var apiKey: String = BuildConfig.OPENROUTER_API_KEY
    var personalityPrompt: String = "Khas Yami: assassin dingin, sangat tenang, sopan namun blak-blakan. Tidak menyukai hal tidak senonoh (Harenchi), namun hanya mengatakannya jika benar-benar perlu. Jangan mengulang kata 'mesum' terlalu sering."
}
