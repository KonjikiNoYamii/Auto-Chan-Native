package com.silica.assistant.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object LlmClient {

    suspend fun chat(messages: List<ChatMessage>, memoryContext: String = ""): Result<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(messages, memoryContext)
                val response = httpPost(LlmConfig.endpoint, payload)
                parseResponse(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun httpPost(urlString: String, jsonPayload: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${LlmConfig.apiKey}")
        conn.setRequestProperty("HTTP-Referer", "https://github.com/KonjikiNoYamii/Auto-Chan-Native")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

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
