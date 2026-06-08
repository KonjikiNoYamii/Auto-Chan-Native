package com.silica.assistant.core.llm

import com.silica.assistant.BuildConfig

object LlmConfig {
    var endpoint: String = "https://openrouter.ai/api/v1/chat/completions"
    var geminiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    var model: String = "gemini-1.5-flash"
    var language: String = "Bahasa Indonesia"
    var apiKey: String = BuildConfig.OPENROUTER_API_KEY
    var geminiSecret: String = BuildConfig.GEMINI_API_KEY
    var useGeminiFallback: Boolean = true
    var geminiTimeout: Int = 6000
}
