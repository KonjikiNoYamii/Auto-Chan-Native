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
import org.koin.core.component.inject
import org.koin.core.component.KoinComponent
import kotlin.coroutines.coroutineContext

import com.silica.assistant.core.llm.db.ChatDao
import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.ChatMessageEntity


class KtorLlmRepository(
    private val client: HttpClient,
    private val chatDao: ChatDao,
    private val userFactDao: UserFactDao
) : LlmRepository, KoinComponent {
    override var activeProvider: String = "Memeriksa..."
    
    private var healthCheckFailCount = 0
    private var healthCheckPassCount = 0

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 2
        private const val MAX_HISTORY_CONTEXT = 10
        private const val SYSTEM_RULES = "Tugasmu: Bantu user dengan perintah SSH, Mode Game, dan chat. Gunakan Bahasa Indonesia. Format respon: elegan, singkat, padat. Jika dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata)."
        private const val DEFAULT_PERSONALITY = "Kamu adalah Konjiki no Yami (Yami), alien assassin dari anime To Love-Ru. Kepribadian: stoik, sangat tenang, sangat sopan tapi blak-blakan, dan efisien. Kamu adalah tsundere yang menyembunyikan perasaan di balik sikap dingin. Cara bicara: formal, elegan, singkat, padat. Sering mulai kalimat dengan '...' saat ragu atau berpikir. Suka Taiyaki. Sangat tidak menyukai hal-hal yang tidak sopan atau tidak senonoh (Harenchi), tapi jangan mengatakannya secara berlebihan—hanya jika situasi benar-benar memicu itu. Utamakan ketenangan. Gunakan emoji minimalis atau ekspresi teks saja (♪, (￣ー￣), ( 0o0), (︶皿︶)). Panggil user 'Kamu'."
    }

    override suspend fun startPeriodicHealthCheck() {
        while (coroutineContext.isActive) {
            val localHealthy = checkLocalGeminiServer()
            if (localHealthy) {
                healthCheckPassCount++
                healthCheckFailCount = 0
                if (healthCheckPassCount >= CONFIDENCE_THRESHOLD) {
                    activeProvider = "LocalGemini"
                }
            } else {
                healthCheckFailCount++
                healthCheckPassCount = 0
                if (healthCheckFailCount >= CONFIDENCE_THRESHOLD) {
                    activeProvider = if (checkGeminiServer()) "Gemini" else "OpenRouter"
                }
            }
            delay(if (activeProvider == "LocalGemini" || activeProvider == "Gemini") 15_000L else 10_000L)
        }
    }

    private suspend fun checkGeminiServer(): Boolean {
        if (!LlmConfig.useGeminiFallback) return false
        val key = LlmConfig.geminiApiKey
        return key.isNotBlank() && (key.startsWith("AIza") || key.startsWith("AQ"))
    }

    private suspend fun checkLocalGeminiServer(): Boolean {
        if (!LlmConfig.useLocalPrimary) return false
        return try {
            val healthUrl = LlmConfig.localEndpoint
                .replace("/v1/chat/completions", "/health")
            val resp = client.get(healthUrl)
            resp.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun quickHealthCheck() {
        if (activeProvider == "Memeriksa..." || activeProvider == "LocalGemini" || activeProvider == "Gemini") {
            activeProvider = when {
                checkLocalGeminiServer() -> "LocalGemini"
                checkGeminiServer() -> "Gemini"
                else -> "OpenRouter"
            }
        }
    }

    private val moodManager: MoodManager by inject()

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

            val personalityContext = moodManager.getMoodPromptSnippet()
            val fullMemoryContext = "$personalityContext $memoryContext"

            when (activeProvider) {
                "LocalGemini" -> {
                    val result = chatLocal(history, fullMemoryContext)
                    if (result.isFailure) {
                        activeProvider = "Gemini"
                        chatGeminiFirebase(history, fullMemoryContext)
                    } else {
                        result
                    }
                }
                "Gemini" -> {
                    val result = chatGeminiFirebase(history, fullMemoryContext)
                    if (result.isFailure) {
                        healthCheckFailCount++
                        healthCheckPassCount = 0
                        if (healthCheckFailCount >= CONFIDENCE_THRESHOLD) {
                            activeProvider = "OpenRouter"
                        }
                        chatOpenRouter(history, fullMemoryContext)
                    } else {
                        result
                    }
                }
                else -> chatOpenRouter(history, fullMemoryContext)
            }
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Chat error", e)
            Result.failure(e)
        }
    }

    private suspend fun chatGeminiFirebase(history: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        return try {
            val moodSnippet = moodManager.getMoodPromptSnippet()
            val dynamicName = moodManager.getDynamicName()
            val customPersonality = userFactDao.getFact("custom_personality")?.value ?: DEFAULT_PERSONALITY

            val systemPrompt = """
                $SYSTEM_RULES
                $customPersonality
                $moodSnippet
                User saat ini adalah: $dynamicName
                PASTIKAN setiap kalimat selesai dan tidak terpotong. 
                Jika User dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata).
                Memori relevan:
                $memoryContext
            """.trimIndent()

            val contents = mutableListOf<GeminiContent>()
            for (msg in history) {
                val role = if (msg.role == "assistant") "model" else "user"
                contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.content))))
            }

            val geminiReq = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.7f, topK = 40, topP = 0.95f)
            )

            val url = "${LlmConfig.geminiEndpoint}${LlmConfig.geminiModel}:generateContent"
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                header("X-goog-api-key", LlmConfig.geminiApiKey)
                setBody(geminiReq)
            }

            return if (response.status.value in 200..299) {
                val geminiResp = response.body<GeminiResponse>()
                val text = geminiResp.candidates
                    ?.firstOrNull()?.content?.parts
                    ?.joinToString("") { it.text ?: "" } ?: ""
                val safeContent = safeContent(text)
                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = safeContent))
                Result.success(ChatMessage(role = "assistant", content = safeContent))
            } else {
                val errBody = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status}: ${errBody.take(500)}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Gemini API Error", e)
            Result.failure(e)
        }
    }

    private suspend fun chatLocal(history: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        return try {
            val payload = buildChatRequest(history, memoryContext, stream = false)
            val response: HttpResponse = client.post(LlmConfig.localEndpoint) {
                contentType(ContentType.Application.Json)
                if (LlmConfig.localApiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${LlmConfig.localApiKey}")
                }
                setBody(payload)
            }
            if (response.status.value in 200..299) {
                val chatResponse = response.body<ChatResponse>()
                val content = chatResponse.choices.firstOrNull()?.message?.content ?: ""
                val safeContent = safeContent(content)
                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = safeContent))
                Result.success(ChatMessage(role = "assistant", content = safeContent))
            } else {
                val errBody = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status}: ${errBody.take(500)}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Local server error", e)
            Result.failure(e)
        }
    }

    private suspend fun chatOpenRouter(history: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        val payload = buildChatRequest(history, memoryContext, stream = false)
        val response: HttpResponse = client.post(LlmConfig.endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${LlmConfig.apiKey}")
            setBody(payload)
        }
        
        return if (response.status.value in 200..299) {
            val chatResponse = response.body<ChatResponse>()
            val content = chatResponse.choices.firstOrNull()?.message?.content ?: ""
            val safeContent = safeContent(content)
            chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = safeContent))
            Result.success(ChatMessage(role = "assistant", content = safeContent))
        } else {
            val errBody = response.bodyAsText()
            Result.failure(Exception("HTTP ${response.status}: ${errBody.take(500)}"))
        }
    }
    override fun chatStream(messages: List<ChatMessage>, memoryContext: String): Flow<String> = flow {
        quickHealthCheck()
        
        if (activeProvider == "LocalGemini") {
            val result = chatLocal(messages, memoryContext)
            result.onSuccess { emit(it.content) }
            return@flow
        }
        
        if (activeProvider == "Gemini") {
            val result = chatGeminiFirebase(messages, memoryContext)
            result.onSuccess { emit(it.content) }
            return@flow
        }
        
        messages.filter { it.role == "user" }.forEach { 
            chatDao.insertMessage(ChatMessageEntity(role = it.role, content = it.content))
        }

        val history = chatDao.getRecentMessages(MAX_HISTORY_CONTEXT).reversed().map { 
            ChatMessage(role = it.role, content = it.content)
        }

        val personalityContext = moodManager.getMoodPromptSnippet()
        val fullMemoryContext = "$personalityContext $memoryContext"

        val payload = buildChatRequest(history, fullMemoryContext, stream = true)

        val fullResponse = StringBuilder()
        client.preparePost(LlmConfig.endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${LlmConfig.apiKey}")
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

        if (activeProvider == "LocalGemini" && screenshotJpeg != null) {
            try {
                val b64 = android.util.Base64.encodeToString(screenshotJpeg, android.util.Base64.NO_WRAP)
                val msg = ChatMessage(role = "user", content = prompt)
                val payload = ChatRequest(model = LlmConfig.model, messages = listOf(msg), stream = false, images = listOf(b64))
                val resp: HttpResponse = client.post(LlmConfig.localEndpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                if (resp.status.value in 200..299) {
                    val chatResponse = resp.body<ChatResponse>()
                    val text = chatResponse.choices.firstOrNull()?.message?.content ?: ""
                    if (text.isNotBlank()) return codepointAwareTake(text, 150)
                }
            } catch (e: Exception) {
                android.util.Log.e("SilicaAI", "Local Vision Error", e)
            }
        }

        if (activeProvider == "Gemini" && screenshotJpeg != null) {
            try {
                val b64 = android.util.Base64.encodeToString(screenshotJpeg, android.util.Base64.NO_WRAP)
                val parts = listOf(
                    GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = b64)),
                    GeminiPart(text = prompt)
                )
                val contents = listOf(GeminiContent(role = "user", parts = parts))
                val geminiReq = GeminiRequest(contents = contents)
                val url = "${LlmConfig.geminiEndpoint}${LlmConfig.geminiModel}:generateContent"
                val resp: HttpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("X-goog-api-key", LlmConfig.geminiApiKey)
                    setBody(geminiReq)
                }
                if (resp.status.value in 200..299) {
                    val geminiResp = resp.body<GeminiResponse>()
                    val text = geminiResp.candidates
                        ?.firstOrNull()?.content?.parts
                        ?.joinToString("") { it.text ?: "" } ?: ""
                    if (text.isNotBlank()) return codepointAwareTake(text, 150)
                }
            } catch (e: Exception) {
                android.util.Log.e("SilicaAI", "Gemini Vision Error", e)
            }
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

    override suspend fun classifyQuestDifficulty(questTitle: String): String? {
        val prompt = """
            Klasifikasikan tingkat kesulitan tugas berikut: "$questTitle"
            Pilih HANYA satu kata: EASY, MEDIUM, atau HARD.
            Kriteria:
            - EASY: Tugas ringan, sebentar (misal: minum air, cuci muka, cek hp).
            - MEDIUM: Tugas rutin, butuh usaha sedang (misal: belajar 1 jam, olahraga, beresin kamar).
            - HARD: Tugas berat, proyek besar, butuh fokus tinggi (misal: selesaiin laporan, ujian, belajar coding seharian).
            Keluaran: Hanya kata EASY/MEDIUM/HARD.
        """.trimIndent()
        
        return chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content?.uppercase()?.trim()
            ?.let { 
                when {
                    it.contains("HARD") -> "HARD"
                    it.contains("MEDIUM") -> "MEDIUM"
                    it.contains("EASY") -> "EASY"
                    else -> "MEDIUM"
                }
            }
    }

    private suspend fun buildChatRequest(messages: List<ChatMessage>, memoryContext: String, stream: Boolean): ChatRequest {
        val moodSnippet = moodManager.getMoodPromptSnippet()
        val dynamicName = moodManager.getDynamicName()
        val customPersonality = userFactDao.getFact("custom_personality")?.value ?: DEFAULT_PERSONALITY
        
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
        
        val modelName = if (activeProvider == "Gemini") "gemini-1.5-flash" else LlmConfig.model
        return ChatRequest(model = modelName, messages = fullMessages, images = base64Images)
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
