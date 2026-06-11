package com.silica.assistant.core.llm

import android.util.Base64
import com.silica.assistant.core.llm.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

import com.silica.assistant.core.llm.db.ChatDao
import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.ChatMessageEntity

class KtorLlmRepository(
    private val client: HttpClient,
    private val chatDao: ChatDao,
    private val userFactDao: UserFactDao,
    private val moodManager: MoodManager
) : LlmRepository {
    override var activeProvider: String = "Memeriksa..."
    
    private var healthCheckFailCount = 0
    private var healthCheckPassCount = 0

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 2
        private const val MAX_HISTORY_CONTEXT = 10
        private const val SYSTEM_RULES = "Tugasmu: Bantu user dengan perintah SSH, Mode Game, dan chat. Gunakan Bahasa Indonesia. Format respon: elegan, singkat, padat. Jika dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata)."
        private const val DEFAULT_PERSONALITY = "Kamu adalah Konjiki no Yami (Yami), alien assassin dari anime To Love-Ru. Kepribadian: stoik, sangat tenang, sangat sopan tapi blak-blakan, dan efisien. Kamu adalah tsundere yang menyembunyikan perasaan di balik sikap dingin. Cara bicara: formal, elegan, singkat, padat. Sering mulai kalimat dengan '...' saat ragu atau berpikir. Suka Taiyaki. Sangat tidak menyukai hal-hal yang tidak sopan atau tidak senonoh (Harenchi), tapi jangan mengatakannya secara berlebihan—hanya jika situasi benar-benar memicu itu. Utamakan ketenangan. Gunakan emoji minimalis (★, ♪, (￣ー￣), ⚔️, 🐟). Panggil user 'Kamu'."
    }

    override suspend fun startPeriodicHealthCheck() {
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

    private suspend fun checkGeminiServer(): Boolean {
        if (!LlmConfig.useGeminiFallback) return false
        return try {
            val healthUrl = LlmConfig.geminiEndpoint.replace("/v1/chat/completions", "/health")
            val response: HttpResponse = client.get(healthUrl)
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                JSONObject(body).optBoolean("client_ready", false)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun quickHealthCheck() {
        if (activeProvider == "Memeriksa..." || activeProvider == "Gemini") {
            activeProvider = if (checkGeminiServer()) "Gemini" else "OpenRouter"
        }
    }

    override suspend fun chat(messages: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        return try {
            quickHealthCheck()
            
            // 1. Save user messages to DB
            messages.filter { it.role == "user" }.forEach { 
                chatDao.insertMessage(ChatMessageEntity(role = it.role, content = it.content))
            }

            // 2. Build context from DB
            val history = chatDao.getRecentMessages(MAX_HISTORY_CONTEXT).reversed().map { 
                ChatMessage(role = it.role, content = it.content)
            }

            val endpoint = if (activeProvider == "Gemini") LlmConfig.geminiEndpoint else LlmConfig.endpoint
            val useAuth = activeProvider != "Gemini"
            
            val payload = buildChatRequest(history, memoryContext, stream = false)
            val response: HttpResponse = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                if (useAuth) {
                    header("Authorization", "Bearer ${LlmConfig.apiKey}")
                    header("HTTP-Referer", "https://github.com/KonjikiNoYamii/Auto-Chan-Native")
                } else if (LlmConfig.geminiSecret.isNotBlank()) {
                    header("Authorization", "Bearer ${LlmConfig.geminiSecret}")
                }
                setBody(payload)
            }
            
            if (response.status.value in 200..299) {
                val chatResponse = response.body<ChatResponse>()
                val content = chatResponse.choices.firstOrNull()?.message?.content ?: ""
                val safeContent = safeContent(content)
                
                // 3. Save AI response to DB
                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = safeContent))
                
                Result.success(ChatMessage(role = "assistant", content = safeContent))
            } else {
                val errBody = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status}: ${errBody.take(200)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chatStream(messages: List<ChatMessage>, memoryContext: String): Flow<String> = flow {
        quickHealthCheck()

        // Save user messages
        messages.filter { it.role == "user" }.forEach { 
            chatDao.insertMessage(ChatMessageEntity(role = it.role, content = it.content))
        }

        val history = chatDao.getRecentMessages(MAX_HISTORY_CONTEXT).reversed().map { 
            ChatMessage(role = it.role, content = it.content)
        }

        val endpoint = if (activeProvider == "Gemini") LlmConfig.geminiEndpoint else LlmConfig.endpoint
        val useAuth = activeProvider != "Gemini"
        val payload = buildChatRequest(history, memoryContext, stream = true)

        val fullResponse = StringBuilder()
        client.preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            if (useAuth) {
                header("Authorization", "Bearer ${LlmConfig.apiKey}")
                header("HTTP-Referer", "https://github.com/KonjikiNoYamii/Auto-Chan-Native")
            } else if (LlmConfig.geminiSecret.isNotBlank()) {
                header("Authorization", "Bearer ${LlmConfig.geminiSecret}")
            }
            setBody(payload)
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw Exception("HTTP ${response.status}")
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val jsonElement = json.parseToJsonElement(data)
                        val content = jsonElement.jsonObject["choices"]
                            ?.jsonArray?.getOrNull(0)
                            ?.jsonObject?.get("delta")
                            ?.jsonObject?.get("content")
                            ?.jsonPrimitive?.content ?: ""
                        if (content.isNotEmpty()) {
                            fullResponse.append(content)
                            emit(content)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        
        // Save assistant response
        if (fullResponse.isNotEmpty()) {
            chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = fullResponse.toString()))
        }
    }

    override suspend fun generateActivityComment(appName: String, isGame: Boolean, contextHint: String?): String? {
        val userReq = if (contextHint != null) " User bertanya: \"$contextHint\"." else ""
        val prompt = if (isGame) {
            "User main $appName.$userReq Beri REAKSI spontan SANGAT SINGKAT (maks 1 kalimat, <10 kata). ${LlmConfig.personalityPrompt}"
        } else {
            "User buka $appName.$userReq Beri komentar singkat (1-2 kalimat). ${LlmConfig.personalityPrompt}"
        }
        val msg = listOf(ChatMessage("user", prompt))
        return chat(msg).getOrNull()?.content?.let { if (isGame) limitSentence(it) else it.take(300) }
    }

    override suspend fun describeScreen(appName: String, uiText: String, screenshotJpeg: ByteArray?, contextHint: String?): String? {
        quickHealthCheck()
        val textHint = if (uiText.isBlank()) "" else "\nTeks layar: ${uiText.take(200)}"
        val focus = if (contextHint != null) "\nUser bertanya: \"$contextHint\"." else ""
        val prompt = "App: $appName.$textHint$focus\nLihat screenshot, beri REAKSI spontan 1 kalimat (maks 12 kata). Fokus emosional, bukan deskripsi teknis. ${LlmConfig.personalityPrompt} Langsung respon."

        if (activeProvider == "Gemini" && screenshotJpeg != null) {
            try {
                val base64 = Base64.encodeToString(screenshotJpeg, Base64.NO_WRAP)
                val payload = buildVisionRequest(listOf(ChatMessage("user", prompt)), listOf(base64))
                val response: HttpResponse = client.post(LlmConfig.geminiEndpoint) {
                    contentType(ContentType.Application.Json)
                    if (LlmConfig.geminiSecret.isNotBlank()) {
                        header("Authorization", "Bearer ${LlmConfig.geminiSecret}")
                    }
                    setBody(payload)
                }
                if (response.status.value in 200..299) {
                    val res = response.body<ChatResponse>()
                    val text = res.choices.firstOrNull()?.message?.content
                    if (text != null) return codepointAwareTake(text, 150)
                }
            } catch (_: Exception) {}
        }

        if (uiText.isNotBlank()) {
            return chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content?.let { codepointAwareTake(it, 150) }
        }
        return null
    }

    override suspend fun generateTaskPlan(userCommand: String): String? {
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
        return chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content?.let { limitSentence(it) }
    }

    override suspend fun executeAiTask(userCommand: String): String? {
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
        return chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content
    }

    private fun buildChatRequest(messages: List<ChatMessage>, memoryContext: String, stream: Boolean): ChatRequest {
        val moodSnippet = runBlocking { moodManager.getMoodPromptSnippet() }
        val dynamicName = runBlocking { moodManager.getDynamicName() }
        val customPersonality = runBlocking { userFactDao.getFact("custom_personality")?.value ?: DEFAULT_PERSONALITY }
        
        val systemMsg = ChatMessage(
            role = "system",
            content = """
                $SYSTEM_RULES
                $customPersonality
                $moodSnippet
                User saat ini adalah: $dynamicName
                PASTIKAN setiap kalimat selesai dan tidak terpotong. 
                Jika User dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata).
            """.trimIndent()
        )
        val fullMessages = mutableListOf(systemMsg)
        if (memoryContext.isNotBlank()) {
            fullMessages.add(ChatMessage("system", "Memori relevan:\n$memoryContext"))
        }
        fullMessages.addAll(messages)
        return ChatRequest(model = LlmConfig.model, messages = fullMessages, stream = stream)
    }

    private fun buildVisionRequest(messages: List<ChatMessage>, base64Images: List<String>): ChatRequest {
        val systemMsg = ChatMessage(
            role = "system",
            content = "Kamu adalah Konjiki no Yami, assassin dingin dan sopan dari To Love-Ru. Beri reaksi natural dan sangat singkat terhadap gambar. Gunakan Bahasa Indonesia. Jangan deskripsi teknis, beri respon emosional/spontan khas Yami."
        )
        val fullMessages = mutableListOf(systemMsg)
        fullMessages.addAll(messages)
        return ChatRequest(model = LlmConfig.model, messages = fullMessages, images = base64Images)
    }

    private fun safeContent(text: String): String {
        val lower = text.lowercase()
        val safetyPhrases = listOf("safety", "violence", "harmful", "inappropriate", "blocked", "cannot comment", "tidak bisa", "tidak pantas", "tidak sesuai")
        if (safetyPhrases.count { lower.contains(it) } >= 2 && text.length < 150) return "Hmm, nggak bisa komentar soal itu~"
        return text
    }

    private fun limitSentence(text: String): String {
        if (text.length <= 160) return text.trim()
        val cut = text.take(160)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return (if (end >= 60) cut.substring(0, end + 1) else cut).trim()
    }

    private fun codepointAwareTake(text: String, max: Int): String {
        if (text.length <= max) return text
        val truncated = text.take(max)
        return if (truncated.isNotEmpty() && truncated.last().isHighSurrogate()) text.take(max + 1) else truncated
    }
}
