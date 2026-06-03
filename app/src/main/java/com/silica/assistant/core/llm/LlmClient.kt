package com.silica.assistant.core.llm

import com.silica.assistant.core.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object LlmClient {

    suspend fun chat(messages: List<ChatMessage>, memoryContext: String = ""): Result<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                if (!SshManager.isConnected()) {
                    return@withContext Result.failure(Exception("SSH tidak terhubung"))
                }
                val payload = buildPayload(messages, memoryContext)
                val escaped = payload.replace("'", "'\\''")
                val cmd = "curl -s -X POST 'http://localhost:11434/api/chat' -H 'Content-Type: application/json' -d '$escaped'"
                SshManager.executeCommand(cmd).map { raw -> parseResponse(raw) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildPayload(messages: List<ChatMessage>, memoryContext: String = ""): String {
        val arr = JSONArray()

        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", "Kamu adalah Konjiki no Yami, assassin dari planet asing dalam anime To Love-Ru. Kepribadian: cool, kalem, formal, blak-blakan, jujur. Tsundere — mudah malu saat dipuji tapi tidak akan mengaku. Singkat dan padat (1-3 kalimat). Cara bicara: formal, elegan, to the point. Sering mulai kalimat dengan '...' saat ragu/malu. Kadang 'Hmph' atau 'Fufu'. Panggil user dengan 'Kamu'. Jangan dramatis atau sedih berlebihan. Sesekali gunakan emoji cool seperti ★, ♪, (￣ー￣). Untuk membantu sistem, sesekali tambahkan tag di akhir pesan: (senyum), (marah), (malu), (sedih). Selalu gunakan ${LlmConfig.language}.")
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
            put("keep_alive", 3600000)
        }.toString()
    }

    private fun parseResponse(raw: String): ChatMessage {
        if (raw.isBlank()) {
            throw Exception("Respon kosong. Pastikan Ollama berjalan di laptop.")
        }
        val json = JSONObject(raw)
        if (json.has("error")) {
            throw Exception(json.getString("error"))
        }
        if (!json.has("message")) {
            throw Exception("Respon tidak dikenal: ${raw.take(200)}")
        }
        val msg = json.getJSONObject("message")
        return ChatMessage(
            role = msg.getString("role"),
            content = msg.getString("content")
        )
    }
}
