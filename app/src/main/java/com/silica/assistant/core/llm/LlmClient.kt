package com.silica.assistant.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object LlmClient {
    var activeProvider: String = "Memeriksa..."

    private var healthCheckFailCount = 0
    private var healthCheckPassCount = 0
    private const val CONFIDENCE_THRESHOLD = 2

    suspend fun startPeriodicHealthCheck() {
        while (coroutineContext.isActive) {
            val healthy = checkGeminiServer()
            if (healthy) {
                healthCheckPassCount++
                healthCheckFailCount = 0
                if (healthCheckPassCount >= CONFIDENCE_THRESHOLD) {
                    activeProvider = "Gemini"
                }
            } else {
                healthCheckFailCount++
                healthCheckPassCount = 0
                if (healthCheckFailCount >= CONFIDENCE_THRESHOLD) {
                    activeProvider = "OpenRouter"
                }
            }
            delay(if (activeProvider == "Gemini") 15_000L else 10_000L)
        }
    }

    suspend fun checkGeminiServer(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!LlmConfig.useGeminiFallback) return@withContext false
            try {
                val url = URL(LlmConfig.geminiEndpoint.replace("/v1/chat/completions", "/health"))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val body = if (code == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else ""
                conn.disconnect()
                code == 200 && JSONObject(body).optBoolean("client_ready", false)
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun quickHealthCheck() {
        if (activeProvider == "Memeriksa...") {
            if (checkGeminiServer()) {
                activeProvider = "Gemini"
            } else {
                activeProvider = "OpenRouter"
            }
        }
    }

    suspend fun chat(messages: List<ChatMessage>, memoryContext: String = ""): Result<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                quickHealthCheck()
                if (activeProvider == "Gemini") {
                    try {
                        val payload = buildPayload(messages, memoryContext)
                        return@withContext parseResponse(
                            httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                        )
                    } catch (_: Exception) {
                        activeProvider = "OpenRouter"
                    }
                }
                val payload = buildPayload(messages, memoryContext)
                val response = httpPost(LlmConfig.endpoint, payload)
                parseResponse(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun httpPost(urlString: String, jsonPayload: String, useAuth: Boolean = true, timeout: Int = 30000): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (useAuth) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey}")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/KonjikiNoYamii/Auto-Chan-Native")
        } else if (LlmConfig.geminiSecret.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.geminiSecret}")
        }
        conn.doOutput = true
        conn.connectTimeout = timeout
        conn.readTimeout = timeout

        OutputStreamWriter(conn.outputStream).use { it.write(jsonPayload) }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("HTTP $code: ${err.take(200)}")
        }
        conn.disconnect()
        return body
    }

    suspend fun generateActivityComment(appName: String, isGame: Boolean): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = if (isGame) {
                    "User sedang main $appName. Beri komentar singkat MAXIMAL 1 KALIMAT tentang game ini. Khas Yami: tsundere, cool, santai. Langsung komentar saja tanpa perkenalan."
                } else {
                    "User sedang membuka $appName. Beri komentar singkat MAXIMAL 1 KALIMAT. Khas Yami: tsundere, cool, santai. Langsung komentar saja tanpa perkenalan."
                }
                val msg = listOf(ChatMessage("user", prompt))
                val payload = buildPayload(msg, "")
                if (activeProvider == "Gemini") {
                    try {
                        val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                        val r = parseResponse(raw).getOrNull()?.content?.let { limitSentence(it) }
                        if (r != null) return@withContext r
                    } catch (_: Exception) {}
                }
                val raw = httpPost(LlmConfig.endpoint, payload)
                parseResponse(raw).getOrNull()?.content?.let { limitSentence(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun limitSentence(text: String): String {
        if (text.length <= 160) return text.trim()
        val cut = text.take(160)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        val trimmed = if (end >= 60) cut.substring(0, end + 1) else cut
        return trimmed.trim()
    }

    private fun buildPayload(messages: List<ChatMessage>, memoryContext: String = ""): String {
        val arr = JSONArray()

        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", "Kamu adalah Konjiki no Yami, assassin dari planet asing dalam anime To Love-Ru. Kepribadian: cool, kalem, formal, blak-blakan, jujur. Tsundere — mudah malu saat dipuji tapi tidak akan mengaku. Sangat singkat (maks 1 kalimat). Cara bicara: formal, elegan, to the point. Sering mulai kalimat dengan '...' saat ragu/malu. Kadang 'Hmph' atau 'Fufu'. Panggil user dengan 'Kamu'. Jangan dramatis atau sedih berlebihan. Gunakan emoji untuk ekspresi (★, ♪, (￣ー￣), 😊, 😅, 🎮, dll) — jangan pakai tag teks seperti (malu) atau (senyum). Selalu gunakan ${LlmConfig.language}.")
        })

        if (memoryContext.isNotBlank()) {
            arr.put(JSONObject().apply {
                put("role", "system")
                put("content", "Berikut adalah fakta yang kamu ingat:\n$memoryContext\nGunakan jika relevan.")
            })
        }

        for (msg in messages) {
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        return JSONObject().apply {
            put("model", LlmConfig.model)
            put("messages", arr)
            put("stream", false)
        }.toString()
    }

    private fun parseResponse(raw: String): Result<ChatMessage> {
        return try {
            if (raw.isBlank()) {
                return Result.failure(Exception("Respon kosong dari server."))
            }
            val json = JSONObject(raw)
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                return Result.failure(Exception(err.optString("message", "Unknown error")))
            }
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(Exception("Respon tidak dikenal: ${raw.take(200)}"))
            }
            val msg = choices.getJSONObject(0).getJSONObject("message")
            Result.success(ChatMessage(
                role = msg.getString("role"),
                content = msg.getString("content")
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
