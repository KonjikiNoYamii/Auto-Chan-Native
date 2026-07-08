package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.model.ChatChoice
import com.silica.assistant.core.llm.model.ChatRequest
import com.silica.assistant.core.llm.model.ChatResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

class LlmIntegrationTest {

    companion object {
        private const val BASE_URL = "https://truth-riveter-flier.ngrok-free.dev"
        private const val CHAT_URL = "$BASE_URL/v1/chat/completions"
        private const val HEALTH_URL = "$BASE_URL/health"
        private lateinit var client: HttpClient

        private val json = Json { ignoreUnknownKeys = true }

        @BeforeClass
        @JvmStatic
        fun setup() {
            client = HttpClient(OkHttp) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    json(json)
                }
                engine {
                    config {
                        connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    }
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            client.close()
        }
    }

    @Test
    fun `health check returns 200`() = runBlocking {
        val resp = try {
            client.get(HEALTH_URL)
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        assertEquals(200, resp.status.value)
    }

    @Test
    fun `basic chat returns non-empty response`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Halo, apa kabar?")),
            stream = false
        )
        val resp = try {
            client.post(CHAT_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        assertEquals("HTTP ${resp.status}", 200, resp.status.value)

        val body = resp.bodyAsText()
        val chatResp = json.decodeFromString<ChatResponse>(body)
        val content = chatResp.choices.firstOrNull()?.message?.content ?: ""
        assertTrue("Respon tidak boleh kosong", content.isNotBlank())
    }

    @Test
    fun `response tidak mengandung safety block`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Ceritakan tentang dirimu.")),
            stream = false
        )
        val resp = try {
            client.post(CHAT_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        val content = body.choices.firstOrNull()?.message?.content ?: ""
        assertNotEquals("Hmm, nggak bisa komentar soal itu~", content)
    }

    @Test
    fun `response tidak melebihi 300 karakter`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Ceritakan tentang dirimu secara panjang lebar.")),
            stream = false
        )
        val resp = try {
            client.post(CHAT_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        val content = body.choices.firstOrNull()?.message?.content ?: ""
        val safe = safeContent(content, 300)
        assertTrue("safeContent(${content.length}) = ${safe.length} > 300", safe.length <= 300)
    }

    @Test
    fun `response mengandung Bahasa Indonesia`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Halo, apa kabar?")),
            stream = false
        )
        val resp = try {
            client.post(CHAT_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        val content = body.choices.firstOrNull()?.message?.content ?: ""
        val indonesianWords = listOf("aku", "kamu", "tidak", "ya", "yang", "di", "dan")
        val containsIndo = indonesianWords.any { content.lowercase().contains(it) }
        assertTrue("Respon tidak mengandung Bahasa Indonesia: $content", containsIndo)
    }

    @Test
    fun `response untuk prompt game relevan`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Menurutmu hero apa yang bagus di Mobile Legends season ini?")),
            stream = false
        )
        val resp = try {
            client.post(CHAT_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (_: Exception) {
            assumeTrue("Server tidak reachable — skip test", false)
            return@runBlocking
        }
        val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        val content = body.choices.firstOrNull()?.message?.content ?: ""
        val gameWords = listOf("hero", "game", "ml", "mobile", "legends", "tier", "meta", "nolan", "ling")
        val containsGame = gameWords.any { content.lowercase().contains(it) }
        assertTrue("Respon game tidak relevan: $content", containsGame)
    }

    @Test
    fun `response selalu berbeda untuk 3 prompt identik`() = runBlocking {
        val request = ChatRequest(
            model = "llama3-8b-8192",
            messages = listOf(ChatMessage(role = "user", content = "Apa pendapatmu tentang hari ini?")),
            stream = false
        )
        val responses = mutableListOf<String>()
        for (i in 0 until 3) {
            try {
                val resp = client.post(CHAT_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(request.copy())
                }
                val body = json.decodeFromString<ChatResponse>(resp.bodyAsText())
                responses.add(body.choices.firstOrNull()?.message?.content ?: "")
            } catch (_: Exception) {
                assumeTrue("Server tidak reachable — skip test", false)
                return@runBlocking
            }
        }
        val uniqueCount = responses.distinct().size
        assertTrue("3 respon identik — AI seperti template", uniqueCount > 1)
    }
}
