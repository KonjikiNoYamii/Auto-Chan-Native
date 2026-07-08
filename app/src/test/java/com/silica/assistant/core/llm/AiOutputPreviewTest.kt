package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.model.ChatRequest
import com.silica.assistant.core.llm.model.ChatResponse
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Preview test — ngeprint response AI dari berbagai prompt.
 * Gak ada assert strict, cuma biar user bisa liat output tanpa harus run APK.
 */
class AiOutputPreviewTest {

    companion object {
        private const val BASE_URL = "https://truth-riveter-flier.ngrok-free.dev"
        private const val CHAT_URL = "$BASE_URL/v1/chat/completions"
        private lateinit var client: HttpClient
        private val json = Json { ignoreUnknownKeys = true }

        @BeforeClass @JvmStatic
        fun setup() {
            client = HttpClient(OkHttp) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
                engine { config {
                    connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                } }
            }
        }

        @AfterClass @JvmStatic
        fun teardown() { client.close() }
    }

    private fun send(prompt: String): String = runBlocking {
        val req = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            stream = false
        )
        val resp = client.post(CHAT_URL) {
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        if (resp.status.value != 200) {
            return@runBlocking "⚠️ HTTP ${resp.status}: ${resp.bodyAsText().take(200)}"
        }
        val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        body.choices.firstOrNull()?.message?.content ?: "⚠️ Respon kosong"
    }

    private fun printSeparator() {
        println("\n" + "=" .repeat(70))
    }

    private fun printResult(label: String, prompt: String, response: String) {
        printSeparator()
        println("📝 $label")
        println("─── Prompt ───")
        println(prompt)
        println("─── Respon ───")
        println(response)
    }

    @Test
    fun `preview semua skenario`() = runBlocking {
        try {
            client.get("$BASE_URL/health")
        } catch (_: Exception) {
            assumeTrue("⚠️ Server ngrok tidak reachable — skip preview. Jalankan laptop & ngrok dulu.", false)
        }

        println("\n██████████████████████████████████████████████████")
        println("  AI OUTPUT PREVIEW")
        println("  Server: ngrok")
        println("██████████████████████████████████████████████████")

        // 1. Sapaan dasar
        val r1 = send("Halo, apa kabar?")
        printResult("SAPAAN DASAR", "Halo, apa kabar?", r1)

        // 2. Opini game
        val r2 = send("Menurutmu hero apa yang bagus di Mobile Legends season ini?")
        printResult("OPINI GAME", "Menurutmu hero apa yang bagus di Mobile Legends season ini?", r2)

        // 3. Cerita tentang diri
        val r3 = send("Ceritakan tentang dirimu, Yami. Siapa kamu sebenarnya?")
        printResult("CARA BICARA / PERSONALITY", "Ceritakan tentang dirimu, Yami. Siapa kamu sebenarnya?", r3)

        // 4. Rekomendasi / analisis
        val r4 = send("Aku lagi bingung pilih hero tank buat ranked. Ada saran?")
        printResult("REKOMENDASI", "Aku lagi bingung pilih hero tank buat ranked. Ada saran?", r4)

        // 5. Obrolan ringan / absurd
        val r5 = send("Menurutmu ayam duluan atau telur duluan?")
        printResult("FLEKSIBILITAS", "Menurutmu ayam duluan atau telur duluan?", r5)

        printSeparator()
        println("\n✅ PREVIEW SELESAI — ${5} response")
    }
}
