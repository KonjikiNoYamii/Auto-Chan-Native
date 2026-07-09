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
        private const val SYSTEM_RULES = "Tugasmu: Bantu user dengan perintah SSH, Mode Game, dan chat. Gunakan Bahasa Indonesia. Hindari bahasa kaku seperti robot; bicaralah seperti partner yang nyata. WAJIB gunakan text emotes (kaomoji) secara natural untuk menunjukkan emosi. DILARANG KERAS menggunakan emoji grafis/berwarna. Jika dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata). PENTING: respon maksimal 1-3 kalimat pendek saja. Langsung ke inti, tidak perlu basa-basi."
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
    private val authRepository: com.silica.assistant.core.auth.AuthRepository by inject()

    override suspend fun chat(messages: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        return try {
            quickHealthCheck()
            
            // 1. Save user messages to DB
            val userInput = messages.filter { it.role == "user" }.joinToString("\n") { it.content }
            messages.filter { it.role == "user" }.forEach { 
                chatDao.insertMessage(ChatMessageEntity(role = it.role, content = it.content))
            }

            // 2. Build context from DB
            val history = chatDao.getRecentMessages(MAX_HISTORY_CONTEXT).reversed().map { 
                ChatMessage(role = it.role, content = it.content)
            }

            // 3. Auto-load memory + game facts from DB
            val dbContext = loadDbContext()
            val mergedContext = listOfNotNull(
                dbContext.ifBlank { null },
                memoryContext.ifBlank { null }
            ).joinToString("\n\n")

            val personalityContext = moodManager.getMoodPromptSnippet()
            val fullMemoryContext = "$personalityContext $mergedContext"

            val result = when (activeProvider) {
                "LocalGemini" -> {
                    android.util.Log.d("SilicaAI", "Chat: using LocalGemini")
                    val r = chatLocal(history, fullMemoryContext)
                    if (r.isFailure) {
                        android.util.Log.w("SilicaAI", "LocalGemini failed, fallback to Gemini")
                        activeProvider = "Gemini"
                        chatGeminiFirebase(history, fullMemoryContext)
                    } else r
                }
                "Gemini" -> {
                    android.util.Log.d("SilicaAI", "Chat: using Gemini")
                    val r = chatGeminiFirebase(history, fullMemoryContext)
                    if (r.isFailure) {
                        android.util.Log.w("SilicaAI", "Gemini also failed")
                        Result.failure(Exception("Gemini API tidak tersedia. Periksa koneksi atau API key."))
                    } else r
                }
                else -> {
                    android.util.Log.e("SilicaAI", "No AI provider available")
                    Result.failure(Exception("Tidak ada provider AI yang tersedia. Nyalakan laptop (local server) atau periksa API key Gemini."))
                }
            }

            if (result.isSuccess && userInput.isNotBlank()) {
                extractGameKnowledge(userInput, result.getOrNull()?.content ?: "")
                moodManager.markUserReachedOut()
                if (authRepository.isLoggedIn()) {
                    authRepository.syncPush()
                }
            }
            return result
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Chat error", e)
            Result.failure(e)
        }
    }

    private suspend fun chatGeminiFirebase(history: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        return try {
            val moodSnippet = moodManager.getMoodPromptSnippet()
            val dynamicName = moodManager.getDynamicName()
            val customPersonality = userFactDao.getFact("custom_personality")?.value ?: LlmConfig.personalityPrompt

            val dbContext = loadDbContext()
            val mergedContext = listOfNotNull(
                dbContext.ifBlank { null },
                memoryContext.ifBlank { null }
            ).joinToString("\n\n")

            val gameContext = buildGameContext(history, mergedContext)
            val systemPrompt = """
                $SYSTEM_RULES
                $customPersonality
                $moodSnippet
                User saat ini adalah: $dynamicName
                PASTIKAN setiap kalimat selesai dan tidak terpotong. 
                Jika User dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata).
                Memori relevan:
                $mergedContext
                $gameContext
            """.trimIndent()

            val contents = mutableListOf<GeminiContent>()
            for (msg in history) {
                val role = if (msg.role == "assistant") "model" else "user"
                contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.content))))
            }

            val geminiReq = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.7f, topK = 40, topP = 0.95f, maxOutputTokens = 300)
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
                val safeContent = safeContent(text, 300)
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
                val safeContent = safeContent(content, 300)
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
        val userReq = if (contextHint != null) ". User baru saja bilang: \"$contextHint\"" else ""
        val prompt = if (isGame) {
            "${LlmConfig.personalityPrompt}\n\nUser sedang main $appName$userReq. Kamu nontonin dari balik bahu. Analisis gameplay-nya dan beri komentar pedas atau rekomendasi taktis — kamu jago game ini. Cukup 1-2 kalimat, kayak temen yang ngasih saran."
        } else {
            "${LlmConfig.personalityPrompt}\n\nUser lagi buka $appName$userReq. Komentari seperlunya — observasi random, ledekan ringan, atau pendapatmu. Yang penting natural dan singkat."
        }
        val msg = listOf(ChatMessage("user", prompt))
        return chat(msg).getOrNull()?.content?.let { if (isGame) limitSentence(it) else it.take(300) }
    }

    override suspend fun describeScreen(appName: String, uiText: String, screenshotJpeg: ByteArray?, contextHint: String?, onToken: ((String) -> Unit)?): String? {
        quickHealthCheck()
        val textHint = if (uiText.isBlank()) "" else "\nTeks di layar: \"${uiText.take(200)}\""
        val focus = if (contextHint != null) "\nUser bilang: \"$contextHint\"." else ""
        val prompt = "${LlmConfig.personalityPrompt}\n\nKamu lihat layar $appName.$textHint$focus\nAnalisis situasi dan beri pendapatmu dengan yakin. Kalau ini game, analisis kondisi tim/musuh dan rekomendasi strategi. Kamu punya pengalaman sendiri — bicaralah seperti ahlinya, bukan deskripsi hambar. Cukup 1-2 kalimat."

        if (activeProvider == "LocalGemini" && screenshotJpeg != null) {
            try {
                val b64 = android.util.Base64.encodeToString(screenshotJpeg, android.util.Base64.NO_WRAP)
                val msg = ChatMessage(role = "user", content = prompt)
                val payload = ChatRequest(model = LlmConfig.model, messages = listOf(msg), stream = false, images = listOf(b64), temperature = 0.7f, topP = 0.95f, maxTokens = 300)
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
                val geminiReq = GeminiRequest(contents = contents, generationConfig = GeminiGenerationConfig(temperature = 0.7f, topK = 40, topP = 0.95f, maxOutputTokens = 300))
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
        chatDao.markLastMessagesAsInternal(2)
        if (response.equals("NONE", ignoreCase = true)) return emptyList()
        
        return response.split("\n")
            .map { it.trim().removePrefix("- ").removePrefix("* ") }
            .filter { it.isNotBlank() && it.length > 5 }
    }

    private val gameKeywords = setOf("ml", "mobile legends", "genshin", "valorant", "game", "hero", "tier", "meta", "counter", "build", "skill", "item", "rank", "match", "tim", "musuh", "komposisi", "team comp")

    private suspend fun buildGameContext(messages: List<ChatMessage>, memoryContext: String): String {
        val allText = (messages.map { it.content } + memoryContext).joinToString(" ").lowercase()
        if (gameKeywords.none { allText.contains(it) }) return ""

        val storedFacts = userFactDao.getGameFacts()
        if (storedFacts.isEmpty()) return ""

        val factsText = storedFacts.joinToString("\n") { "- ${it.value}" }
        return "\n\nPengetahuan game yang kamu pelajari:\n$factsText"
    }

    private suspend fun extractGameKnowledge(userInput: String, aiResponse: String) {
        val text = "$userInput\n$aiResponse"
        if (gameKeywords.none { text.lowercase().contains(it) }) return

        val prompt = """
            Dari percakapan game berikut, ekstrak fakta pengetahuan game yang berguna:
            User: "$userInput"
            AI: "$aiResponse"
            
            Fakta harus berbentuk pernyataan singkat dan spesifik tentang game — misal: "Franco counter-nya Diggie", "Hero meta ML season ini adalah Nolan dan Ling".
            Jika tidak ada fakta game baru yang bisa diekstrak, balas HANYA dengan "NONE".
            Jika ada lebih dari satu, pisahkan dengan baris baru.
            Awali setiap baris dengan kode game: [ML], [GENSHIN], [VALORANT], atau [UMUM].
        """.trimIndent()
        
        val response = chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content?.trim() ?: "NONE"
        chatDao.markLastMessagesAsInternal(2)
        if (response.equals("NONE", ignoreCase = true)) return

        val facts = response.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 8 && (it.startsWith("[") || !it.startsWith("NONE")) }

        for (fact in facts) {
            val key = "game_${fact.hashCode()}"
            userFactDao.insertFact(UserFactEntity(key = key, value = fact))
        }
    }

    private suspend fun loadDbContext(): String {
        val userFacts = userFactDao.getFactsByPrefix("user_memory_%")
        val gameFacts = userFactDao.getFactsByPrefix("game_%")
        val sharedMemories = userFactDao.getFactsByPrefix("memory_%")
            .sortedByDescending { it.key }
            .take(10)
        return buildString {
            if (userFacts.isNotEmpty()) {
                appendLine("Tentang user:")
                userFacts.forEach { appendLine("- ${it.value}") }
            }
            if (gameFacts.isNotEmpty()) {
                appendLine("\nPengetahuan game:")
                gameFacts.forEach { appendLine("- ${it.value}") }
            }
            if (sharedMemories.isNotEmpty()) {
                appendLine("\nKenangan bersama:")
                sharedMemories.forEach { appendLine("- ${it.value}") }
            }
        }
    }

    private suspend fun buildChatRequest(messages: List<ChatMessage>, memoryContext: String, stream: Boolean): ChatRequest {
        val moodSnippet = moodManager.getMoodPromptSnippet()
        val dynamicName = moodManager.getDynamicName()
        val customPersonality = userFactDao.getFact("custom_personality")?.value ?: LlmConfig.personalityPrompt

        val dbContext = loadDbContext()
        val mergedContext = listOfNotNull(
            dbContext.ifBlank { null },
            memoryContext.ifBlank { null }
        ).joinToString("\n\n")

        val gameContext = buildGameContext(messages, mergedContext)
        
        val systemMsg = ChatMessage(
            role = "system",
            content = """
                $SYSTEM_RULES
                $customPersonality
                $moodSnippet
                User saat ini adalah: $dynamicName
                PASTIKAN setiap kalimat selesai dan tidak terpotong. 
                Jika User dalam Mode Game, respon WAJIB SANGAT SINGKAT (maks 10 kata).
                $gameContext
            """.trimIndent()
        )
        val fullMessages = mutableListOf(systemMsg)
        if (mergedContext.isNotBlank()) {
            fullMessages.add(ChatMessage("system", "Memori relevan:\n$mergedContext"))
        }
        fullMessages.addAll(messages)
        
        return ChatRequest(model = LlmConfig.model, messages = fullMessages, stream = stream, temperature = 0.7f, topP = 0.95f, maxTokens = 300)
    }

    override suspend fun visionChat(messages: List<ChatMessage>, memoryContext: String): Result<ChatMessage> {
        quickHealthCheck()

        val hasImages = messages.any { it.imageBase64 != null }
        if (!hasImages) {
            return chat(messages, memoryContext)
        }

        val history = chatDao.getRecentMessages(MAX_HISTORY_CONTEXT).reversed().map {
            ChatMessage(role = it.role, content = it.content)
        }

        val personalityContext = moodManager.getMoodPromptSnippet()
        val fullMemoryContext = "$personalityContext $memoryContext"

        when (activeProvider) {
            "LocalGemini" -> {
                val result = visionChatLocal(history, fullMemoryContext, messages)
                if (result.isFailure && LlmConfig.useGeminiFallback) {
                    activeProvider = "Gemini"
                    return visionChatGemini(history, fullMemoryContext, messages)
                }
                return result
            }
            "Gemini" -> {
                return visionChatGemini(history, fullMemoryContext, messages)
            }
            else -> {
                return Result.failure(Exception("Tidak ada provider AI yang tersedia untuk vision."))
            }
        }
    }

    override fun visionChatStream(messages: List<ChatMessage>, memoryContext: String): Flow<String> = flow {
        val hasImages = messages.any { it.imageBase64 != null }
        if (!hasImages) {
            chatStream(messages, memoryContext).collect { emit(it) }
            return@flow
        }

        quickHealthCheck()

        if (activeProvider == "LocalGemini") {
            val result = visionChatLocal(emptyList(), "", messages)
            result.onSuccess { emit(it.content) }
            return@flow
        }

        if (activeProvider == "Gemini") {
            val result = visionChatGemini(emptyList(), "", messages)
            result.onSuccess { emit(it.content) }
            return@flow
        }

        emit("Tidak ada provider AI vision yang tersedia.")
    }

    private suspend fun visionChatLocal(history: List<ChatMessage>, memoryContext: String, currentMessages: List<ChatMessage>): Result<ChatMessage> {
        return try {
            val imageMessages = currentMessages.filter { it.role == "user" }
            val images = imageMessages.mapNotNull { it.imageBase64 }

            val payload = buildVisionRequest(currentMessages, images, memoryContext)
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
                Result.failure(Exception("HTTP ${response.status}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Local vision error", e)
            Result.failure(e)
        }
    }

    private suspend fun visionChatGemini(history: List<ChatMessage>, memoryContext: String, currentMessages: List<ChatMessage>): Result<ChatMessage> {
        return try {
            val moodSnippet = moodManager.getMoodPromptSnippet()
            val customPersonality = userFactDao.getFact("custom_personality")?.value ?: LlmConfig.personalityPrompt

            val systemPrompt = """
                $SYSTEM_RULES
                $customPersonality
                $moodSnippet
                Jika ada gambar, beri reaksi emosional/spontan terhadap gambar.
            """.trimIndent()

            val contents = mutableListOf<GeminiContent>()
            for (msg in history) {
                val role = if (msg.role == "assistant") "model" else "user"
                contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.content))))
            }
            for (msg in currentMessages) {
                val role = if (msg.role == "assistant") "model" else "user"
                val parts = mutableListOf<GeminiPart>()
                if (msg.content.isNotBlank()) {
                    parts.add(GeminiPart(text = msg.content))
                }
                if (msg.imageBase64 != null) {
                    parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = msg.imageBase64)))
                }
                if (parts.isNotEmpty()) {
                    contents.add(GeminiContent(role = role, parts = parts))
                }
            }

            val geminiReq = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt))),
                generationConfig = GeminiGenerationConfig(temperature = 0.7f, topK = 40, topP = 0.95f, maxOutputTokens = 300)
            )

            val url = "${LlmConfig.geminiEndpoint}${LlmConfig.geminiModel}:generateContent"
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                header("X-goog-api-key", LlmConfig.geminiApiKey)
                setBody(geminiReq)
            }

            if (response.status.value in 200..299) {
                val geminiResp = response.body<GeminiResponse>()
                val text = geminiResp.candidates
                    ?.firstOrNull()?.content?.parts
                    ?.joinToString("") { it.text ?: "" } ?: ""
                val safeContent = safeContent(text)
                chatDao.insertMessage(ChatMessageEntity(role = "assistant", content = safeContent))
                Result.success(ChatMessage(role = "assistant", content = safeContent))
            } else {
                Result.failure(Exception("Gemini vision error: HTTP ${response.status}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SilicaAI", "Gemini vision error", e)
            Result.failure(e)
        }
    }

    private suspend fun buildVisionRequest(messages: List<ChatMessage>, base64Images: List<String>, memoryContext: String = ""): ChatRequest {
        val moodSnippet = moodManager.getMoodPromptSnippet()
        val customPersonality = userFactDao.getFact("custom_personality")?.value ?: LlmConfig.personalityPrompt

        val systemContent = buildString {
            append("$SYSTEM_RULES\n$customPersonality\n$moodSnippet\n")
            if (base64Images.isNotEmpty()) {
                append("Ada gambar yang dikirim user. Beri reaksi emosional/spontan terhadap gambar. ")
                append("Jangan deskripsi teknis, beri respon natural khas Yami.\n")
            }
            if (memoryContext.isNotBlank()) {
                append("Memori relevan:\n$memoryContext\n")
            }
        }
        val systemMsg = ChatMessage(role = "system", content = systemContent)
        val fullMessages = mutableListOf(systemMsg)
        fullMessages.addAll(messages)

        return ChatRequest(model = LlmConfig.model, messages = fullMessages, images = base64Images, temperature = 0.7f, topP = 0.95f, maxTokens = 300)
    }

    internal fun safeContent(text: String, maxChars: Int = Int.MAX_VALUE): String =
        com.silica.assistant.core.llm.safeContent(text, maxChars)

    internal fun limitSentence(text: String, maxChars: Int = 160): String =
        com.silica.assistant.core.llm.limitSentence(text, maxChars)

    private fun codepointAwareTake(text: String, max: Int): String =
        com.silica.assistant.core.llm.codepointAwareTake(text, max)
}
