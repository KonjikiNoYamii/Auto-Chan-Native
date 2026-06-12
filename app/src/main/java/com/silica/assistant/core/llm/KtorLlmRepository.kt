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
        private const val SYSTEM_RULES = "Tugasmu: Bantu user dengan perintah SSH, Mode Game, dan chat. Gunakan Bahasa Indonesia. Format respon: elegan, santai, namun tetap sopan. Hindari bahasa kaku seperti robot; bicaralah seperti partner yang nyata. WAJIB gunakan text emotes (kaomoji) secara natural untuk menunjukkan emosi. DILARANG KERAS menggunakan emoji grafis/berwarna. Jika dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata)."
        private const val DEFAULT_PERSONALITY = "Kamu adalah Yami, alien assassin yang tenang dan sopan. Kamu bicara dengan gaya elegan, sedikit stoik, namun natural. Gunakan variasi kata, jeda seperti 'Hmm...', atau 'Ah,' sesekali agar tidak terasa seperti template. Jangan selalu menjawab dengan pola yang sama. Tunjukkan bahwa kamu benar-benar peduli pada partner kamu dengan cara yang halus. Gunakan kaomoji yang bervariasi dari yang ekspresif sampai yang stoik/cool seperti ( -_ -), (¬_¬), atau (─‿─). Kamu suka Taiyaki. Kamu tidak suka hal yang tidak sopan (Harenchi). Responlah dengan cara yang hidup dan bermakna."
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
                    activeProvider = if (checkGeminiServer()) "Gemini" else "Tidak Ada"
                }
            }
            delay(if (activeProvider == "LocalGemini" || activeProvider == "Gemini") 15_000L else 10_000L)
        }
    }

    private suspend fun checkGeminiServer(): Boolean {
        if (!LlmConfig.useGeminiFallback) return false
        val key = LlmConfig.geminiApiKey
        if (key.isBlank()) {
            android.util.Log.d("SilicaAI", "Gemini check: key is blank")
            return false
        }
        return try {
            val resp = client.get("https://generativelanguage.googleapis.com/v1beta/models") {
                header("X-goog-api-key", key)
            }
            val ok = resp.status.value in 200..299
            android.util.Log.d("SilicaAI", "Gemini real check: ${resp.status} -> $ok")
            ok
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Gemini real check failed: ${e.message}")
            false
        }
    }

    private suspend fun checkLocalGeminiServer(): Boolean {
        if (!LlmConfig.useLocalPrimary) return false
        return try {
            val healthUrl = LlmConfig.localEndpoint
                .replace("/v1/chat/completions", "/health")
            android.util.Log.d("SilicaAI", "Health check: $healthUrl")
            val resp = client.get(healthUrl)
            android.util.Log.d("SilicaAI", "Health check response: ${resp.status}")
            resp.status.value in 200..299
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Health check failed: ${e.message}")
            false
        }
    }

    private suspend fun quickHealthCheck() {
        if (activeProvider == "Memeriksa..." || activeProvider == "LocalGemini" || activeProvider == "Gemini") {
            coroutineScope {
                val local = async { checkLocalGeminiServer() }
                val remote = async { checkGeminiServer() }
                
                activeProvider = when {
                    local.await() -> "LocalGemini"
                    remote.await() -> "Gemini"
                    else -> "Tidak Ada"
                }
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
                    android.util.Log.d("SilicaAI", "Chat: using LocalGemini")
                    val result = chatLocal(history, fullMemoryContext)
                    if (result.isFailure) {
                        android.util.Log.w("SilicaAI", "LocalGemini failed, fallback to Gemini")
                        activeProvider = "Gemini"
                        chatGeminiFirebase(history, fullMemoryContext)
                    } else {
                        result
                    }
                }
                "Gemini" -> {
                    android.util.Log.d("SilicaAI", "Chat: using Gemini")
                    val result = chatGeminiFirebase(history, fullMemoryContext)
                    if (result.isFailure) {
                        android.util.Log.w("SilicaAI", "Gemini also failed")
                        Result.failure(Exception("Gemini API tidak tersedia. Periksa koneksi atau API key."))
                    } else {
                        result
                    }
                }
                else -> {
                    android.util.Log.e("SilicaAI", "No AI provider available")
                    Result.failure(Exception("Tidak ada provider AI yang tersedia. Nyalakan laptop (local server) atau periksa API key Gemini."))
                }
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
            android.util.Log.d("SilicaAI", "Gemini URL: $url")
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
        
        emit("Tidak ada provider AI yang tersedia. Nyalakan laptop atau periksa API key Gemini.")
        return@flow
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

    override suspend fun describeScreen(appName: String, uiText: String, screenshotJpeg: ByteArray?, contextHint: String?, onToken: ((String) -> Unit)?): String? {
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

    override suspend fun extractUserFacts(text: String): List<String> {
        val prompt = """
            Ekstrak fakta penting tentang user dari pesan berikut: "$text"
            Fakta harus berupa pernyataan orang ketiga yang singkat, contoh: "User suka kopi", "User tinggal di Jakarta", "User adalah mahasiswa".
            Abaikan informasi yang tidak penting. Jika tidak ada fakta baru, balas HANYA dengan kata "NONE".
            Jika ada lebih dari satu, pisahkan dengan baris baru.
        """.trimIndent()
        
        val response = chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content?.trim() ?: "NONE"
        if (response.equals("NONE", ignoreCase = true)) return emptyList()
        
        return response.split("\n")
            .map { it.trim().removePrefix("- ").removePrefix("* ") }
            .filter { it.isNotBlank() && it.length > 5 }
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
