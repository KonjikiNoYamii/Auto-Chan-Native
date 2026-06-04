package com.silica.assistant.core.llm

import com.silica.assistant.BuildConfig

object LlmConfig {
    var endpoint: String = "https://openrouter.ai/api/v1/chat/completions"
    var model: String = "openrouter/free"
    var language: String = "Bahasa Indonesia"
    var apiKey: String = BuildConfig.OPENROUTER_API_KEY
}
