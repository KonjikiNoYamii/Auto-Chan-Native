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
                        val payload = buildPayload(messages, memoryContext, stream = false)
                        return@withContext parseResponse(
                            httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                        )
                    } catch (_: Exception) {
                        activeProvider = "OpenRouter"
                    }
                }
                val payload = buildPayload(messages, memoryContext, stream = false)
                val response = httpPost(LlmConfig.endpoint, payload)
                parseResponse(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun chatStream(
        messages: List<ChatMessage>,
        memoryContext: String = "",
        onToken: (String) -> Unit
    ): Result<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                quickHealthCheck()
                if (activeProvider == "Gemini") {
                    try {
                        return@withContext chatStreamGemini(messages, memoryContext, onToken)
                    } catch (_: Exception) {
                        activeProvider = "OpenRouter"
                    }
                }
                val payload = buildPayload(messages, memoryContext, stream = false)
                val response = httpPost(LlmConfig.endpoint, payload)
                val result = parseResponse(response)
                result.getOrNull()?.let { onToken(it.content) }
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun chatStreamGemini(
        messages: List<ChatMessage>,
        memoryContext: String,
        onToken: (String) -> Unit
    ): Result<ChatMessage> {
        val payload = buildPayload(messages, memoryContext, stream = true)
        val url = URL(LlmConfig.geminiEndpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")
        if (LlmConfig.geminiSecret.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.geminiSecret}")
        }
        conn.doOutput = true
        conn.connectTimeout = LlmConfig.geminiTimeout
        conn.readTimeout = 0

        OutputStreamWriter(conn.outputStream).use { it.write(payload) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("HTTP $code: ${err.take(200)}")
        }

        val reader = conn.inputStream.bufferedReader()
        val fullContent = StringBuilder()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        if (json.has("error")) {
                            val errMsg = json.getJSONObject("error").optString("message", "Stream error")
                            throw Exception(errMsg)
                        }
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullContent.append(content)
                                onToken(content)
                            }
                        }
                    } catch (e: Exception) {
                        if (e.message == "Stream error") throw e
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val resultText = fullContent.toString()
        if (resultText.isBlank()) throw Exception("Respon kosong dari server.")
        val safe = safeContent(resultText)
        return Result.success(ChatMessage(role = "assistant", content = safe))
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

    private suspend fun httpPostStream(
        urlString: String,
        jsonPayload: String,
        useAuth: Boolean,
        timeout: Int,
        onToken: (String) -> Unit
    ): String? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "text/event-stream")
        if (useAuth) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey}")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/KonjikiNoYamii/Auto-Chan-Native")
        } else if (LlmConfig.geminiSecret.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.geminiSecret}")
        }
        conn.doOutput = true
        conn.connectTimeout = timeout
        conn.readTimeout = 0

        OutputStreamWriter(conn.outputStream).use { it.write(jsonPayload) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            throw Exception("HTTP $code: ${err.take(200)}")
        }

        val reader = conn.inputStream.bufferedReader()
        val fullContent = StringBuilder()
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        if (json.has("error")) {
                            val errMsg = json.getJSONObject("error").optString("message", "Stream error")
                            throw Exception(errMsg)
                        }
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullContent.append(content)
                                onToken(content)
                            }
                        }
                    } catch (e: Exception) {
                        if (e.message == "Stream error") throw e
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return fullContent.toString().ifBlank { null }
    }

    suspend fun generateActivityComment(appName: String, isGame: Boolean, onToken: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = if (isGame) {
                    "User sedang main $appName. Beri REAKSI singkat 1 kalimat. Jangan mendeskripsikan game-nya, tapi berikan REAKSI spontan seolah kamu ikut menonton. ${LlmConfig.personalityPrompt} Langsung komentar saja."
                } else {
                    "User sedang membuka $appName. Beri REAKSI pendek 1-2 kalimat tentang situasinya. Jangan cuma deskripsi fitur, tapi berikan REAKSI natural. ${LlmConfig.personalityPrompt} Langsung komentar saja."
                }
                val msg = listOf(ChatMessage("user", prompt))
                val payload = buildPayload(msg, "", stream = onToken != null)
                if (activeProvider == "Gemini") {
                    try {
                        val text = if (onToken != null) {
                            httpPostStream(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout, onToken = onToken)
                        } else {
                            val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                            parseResponse(raw).getOrNull()?.content
                        }
                        val r = text?.let { c -> if (isGame) limitSentence(c) else c.take(300) }
                        if (r != null) return@withContext r
                    } catch (_: Exception) {}
                }
                val raw = httpPost(LlmConfig.endpoint, payload)
                val result = parseResponse(raw).getOrNull()?.content
                if (result != null) if (isGame) limitSentence(result) else result.take(300) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun generateScreenComment(appName: String, uiText: String, onToken: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = "User sedang membuka $appName. Konten layar: $uiText. Beri REAKSI pendek 1 kalimat. Fokus pada reaksi emosional/spontan terhadap situasi di layar, jangan sekadar membacakan teksnya. ${LlmConfig.personalityPrompt} Langsung komentar saja."
                val msg = listOf(ChatMessage("user", prompt))
                val payload = buildPayload(msg, "", stream = onToken != null)
                if (activeProvider == "Gemini") {
                    try {
                        val text = if (onToken != null) {
                            httpPostStream(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout, onToken = onToken)
                        } else {
                            val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                            parseResponse(raw).getOrNull()?.content
                        }
                        val r = text?.let { it.take(200) }
                        if (r != null) return@withContext r
                    } catch (_: Exception) {}
                }
                val raw = httpPost(LlmConfig.endpoint, payload)
                parseResponse(raw).getOrNull()?.content?.take(200)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun describeScreen(appName: String, uiText: String, screenshotJpeg: ByteArray?, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                quickHealthCheck()
                val textHint = if (uiText.isBlank()) "" else "\nTeks layar: ${uiText.take(200)}"
                val focus = if (contextHint != null) "\nUser minta dikomentari soal: $contextHint" else ""
                val prompt = "User lagi buka $appName.$textHint$focus\nLihat screenshot, beri REAKSI spontan 1 kalimat natural tentang situasinya (misal: menang, kalah, momen seru, atau situasi lucu). Jangan mendeskripsikan secara teknis (seperti 'ada tombol X'), tapi berikan REAKSI emosional. ${LlmConfig.personalityPrompt} Bahasa Indonesia. Langsung respon."

                // 1) Try Gemini vision (with screenshot)
                if (activeProvider == "Gemini" && screenshotJpeg != null) {
                    try {
                        val base64 = android.util.Base64.encodeToString(screenshotJpeg, android.util.Base64.NO_WRAP)
                        val payload = buildVisionPayload(listOf(ChatMessage("user", prompt)), listOf(base64), stream = onToken != null)
                        val text = if (onToken != null) {
                            httpPostStream(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout + 20000, onToken = onToken)
                        } else {
                            val raw = httpPostJson(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout + 20000)
                            parseResponse(raw).getOrNull()?.content
                        }
                        if (text != null) return@withContext codepointAwareTake(text, 150)
                    } catch (_: Exception) {}
                }

                // 2) Vision failed + no screen text → can't describe
                if (uiText.isBlank()) {
                    throw Exception("Layar kosong (tidak ada teks)")
                }

                // 3) Try Gemini text-only (has text hint but no screenshot)
                if (activeProvider == "Gemini") {
                    try {
                        val msg = listOf(ChatMessage("user", prompt))
                        val payload = buildPayload(msg, "")
                        val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout)
                        val r = parseResponse(raw).getOrNull()?.content
                        if (r != null) return@withContext codepointAwareTake(r, 150)
                    } catch (e: Exception) {
                        throw Exception("Vision & Text fallback gagal: ${e.message}")
                    }
                }

                throw Exception("Provider tidak mendukung vision/text fallback saat ini")
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun codepointAwareTake(text: String, max: Int): String {
        if (text.length <= max) return text
        val truncated = text.take(max)
        return if (truncated.isNotEmpty() && truncated.last().isHighSurrogate()) {
            text.take(max + 1)
        } else truncated
    }

    private fun buildVisionPayload(messages: List<ChatMessage>, base64Images: List<String>, stream: Boolean = false): String {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", "Kamu adalah Konjiki no Yami, assassin dari planet asing dalam anime To Love-Ru. Kepribadian: cool, kalem, formal, blak-blakan, jujur. Tsundere — mudah malu saat dipuji tapi tidak akan mengaku. Gunakan Bahasa Indonesia.")
        })
        for (msg in messages) {
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        return JSONObject().apply {
            put("model", LlmConfig.model)
            put("messages", arr)
            put("images", JSONArray(base64Images))
            put("stream", stream)
        }.toString()
    }

    private fun httpPostJson(urlString: String, jsonPayload: String, useAuth: Boolean = true, timeout: Int = 30000): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (useAuth) {
            conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey}")
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

    private fun limitSentence(text: String): String {
        if (text.length <= 160) return text.trim()
        val cut = text.take(160)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        val trimmed = if (end >= 60) cut.substring(0, end + 1) else cut
        return trimmed.trim()
    }

    private fun buildPayload(messages: List<ChatMessage>, memoryContext: String = "", stream: Boolean = false): String {
        val arr = JSONArray()

        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", "Kamu adalah Konjiki no Yami, assassin dari planet asing dalam anime To Love-Ru. Kepribadian: cool, kalem, formal, blak-blakan, jujur. Tsundere — mudah malu saat dipuji tapi tidak akan mengaku. Cara bicara: formal, elegan, to the point. Sering mulai kalimat dengan '...' saat ragu/malu. Kadang 'Hmph' atau 'Fufu'. Panggil user dengan 'Kamu'. Jangan dramatis atau sedih berlebihan. Gunakan emoji untuk ekspresi (★, ♪, (￣ー￣), 😊, 😅, 🎮, dll) — jangan pakai tag teks seperti (malu) atau (senyum). Selalu gunakan ${LlmConfig.language}.")
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
            put("stream", stream)
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
            val content = safeContent(msg.getString("content"))
            Result.success(ChatMessage(
                role = msg.getString("role"),
                content = content
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun safeContent(text: String): String {
        val lower = text.lowercase()
        val safetyPhrases = listOf(
            "safety", "violence", "harmful", "inappropriate",
            "blocked", "cannot comment",
            "tidak bisa", "tidak pantas", "tidak sesuai",
        )
        val matchCount = safetyPhrases.count { lower.contains(it) }
        if (matchCount >= 2 && text.length < 150) {
            return "Hmm, nggak bisa komentar soal itu~"
        }
        return text
    }
}
