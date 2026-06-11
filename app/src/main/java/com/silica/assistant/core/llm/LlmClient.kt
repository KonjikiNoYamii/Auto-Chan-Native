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

    suspend fun generateActivityComment(appName: String, isGame: Boolean, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val userReq = if (contextHint != null) " User bertanya: \"$contextHint\"." else ""
                val prompt = if (isGame) {
                    "User main $appName.$userReq Beri REAKSI spontan SANGAT SINGKAT (maks 1 kalimat, <10 kata). ${LlmConfig.personalityPrompt}"
                } else {
                    "User buka $appName.$userReq Beri komentar singkat (1-2 kalimat). ${LlmConfig.personalityPrompt}"
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

    suspend fun generateScreenComment(appName: String, uiText: String, contextHint: String? = null, onToken: ((String) -> Unit)? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val userReq = if (contextHint != null) " User bertanya: \"$contextHint\"." else ""
                val prompt = "Konteks: $appName. Layar: $uiText.$userReq Beri REAKSI 1 kalimat pendek. Jangan bacakan teks, beri reaksi natural. ${LlmConfig.personalityPrompt}"
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
                val focus = if (contextHint != null) "\nUser bertanya: \"$contextHint\"." else ""
                val prompt = "App: $appName.$textHint$focus\nLihat screenshot, beri REAKSI spontan 1 kalimat (maks 12 kata). Fokus emosional, bukan deskripsi teknis. ${LlmConfig.personalityPrompt} Langsung respon."

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
            put("content", "Kamu adalah Konjiki no Yami, assassin dingin dan sopan dari To Love-Ru. Beri reaksi natural dan sangat singkat terhadap gambar. Gunakan Bahasa Indonesia. Jangan deskripsi teknis, beri respon emosional/spontan khas Yami.")
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
            put("content", "Kamu adalah Konjiki no Yami (Yami), alien assassin dari anime To Love-Ru. Kepribadian: stoik, sangat tenang, sangat sopan tapi blak-blakan, dan efisien. Kamu adalah tsundere yang menyembunyikan perasaan di balik sikap dingin. Cara bicara: formal, elegan, singkat, padat. Sering mulai kalimat dengan '...' saat ragu atau berpikir. Suka Taiyaki. Sangat tidak menyukai hal-hal yang tidak sopan atau tidak senonoh (Harenchi), tapi jangan mengatakannya secara berlebihan—hanya jika situasi benar-benar memicu itu. Utamakan ketenangan. Gunakan emoji minimalis (★, ♪, (￣ー￣), ⚔️, 🐟). Panggil user 'Kamu'. Gunakan ${LlmConfig.language}. PASTIKAN setiap kalimat selesai dan tidak terpotong. Jika User dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata).")
        })

        if (memoryContext.isNotBlank()) {
            arr.put(JSONObject().apply {
                put("role", "system")
                put("content", "Memori relevan:\n$memoryContext")
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

    suspend fun generateTaskPlan(userCommand: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                quickHealthCheck()
                val prompt = """
Kamu adalah Yami, asisten AI yang sangat cakap.
User memberi perintah: "$userCommand"

Tugasmu:
1. Pahami apa yang user minta
2. Jika perlu kode/program, tulis kode lengkap dalam blok kode (```)
3. Jika perlu perintah shell/SSH, sebutkan command yang tepat
4. Beri rencana eksekusi yang jelas dan langsung

Format:
RENCANA: [judul singkat]
[Penjelasan & langkah-langkah]

[Kode/program jika ada dalam blok ```]

PENTING: Langsung jawab, jangan minta konfirmasi dulu dalam jawaban ini.
${LlmConfig.personalityPrompt}
                """.trimIndent()
                val msg = listOf(ChatMessage("user", prompt))
                val payload = buildPayload(msg, "", stream = false)
                if (activeProvider == "Gemini") {
                    try {
                        val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout + 10000)
                        val r = parseResponse(raw).getOrNull()?.content
                        if (r != null) return@withContext limitSentence(r)
                    } catch (_: Exception) {}
                }
                val raw = httpPost(LlmConfig.endpoint, payload)
                parseResponse(raw).getOrNull()?.content?.let { limitSentence(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun executeAiTask(userCommand: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                quickHealthCheck()
                val prompt = """
Kamu adalah asisten AI yang menghasilkan output ACTION terstruktur.
Perintah user: "$userCommand"
User sudah menyetujui rencana. LANGSUNG EKSEKUSI.

Output HANYA format ACTION berikut, TANPA teks lain:

ACTION: create_folder
FILE: kalkulator

ACTION: create_file
FILE: kalkulator/main.py
CODE:
```python
print("hello")
```

ACTION: run_command
COMMAND: cd SilicaProjects/kalkulator && python3 main.py

ACTION: selesai
RESULT: Selesai! Program berhasil dibuat.

ATURAN:
- Semua path RELATIVE terhadap SilicaProjects/
- Untuk folder: ACTION: create_folder
- Untuk file: ACTION: create_file + FILE: path + CODE di blok ```
- Untuk perintah shell: ACTION: run_command + COMMAND: ...
- ACTION: selesai + RESULT: pesan akhir
- JANGAN output apapun di luar format ACTION di atas
- JANGAN minta konfirmasi
                """.trimIndent()
                val msg = listOf(ChatMessage("user", prompt))
                val payload = buildPayload(msg, "", stream = false)
                if (activeProvider == "Gemini") {
                    try {
                        val raw = httpPost(LlmConfig.geminiEndpoint, payload, useAuth = false, timeout = LlmConfig.geminiTimeout + 20000)
                        val r = parseResponse(raw).getOrNull()?.content
                        if (r != null) return@withContext r
                    } catch (_: Exception) {}
                }
                val raw = httpPost(LlmConfig.endpoint, payload)
                parseResponse(raw).getOrNull()?.content
            } catch (_: Exception) {
                null
            }
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
