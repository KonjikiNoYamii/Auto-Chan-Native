package com.silica.assistant.core.llm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

import kotlinx.coroutines.*

object MemoryManager {
    private const val PREFS_NAME = "llm_memories"
    private const val KEY_MEMORIES = "memories"

    fun getMemories(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEMORIES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun aiAutoExtract(context: Context, userInput: String) {
        val facts = LlmClient.extractUserFacts(userInput)
        for (fact in facts) {
            addMemory(context, fact)
        }
    }

    fun addMemory(context: Context, fact: String) {
        val mems = getMemories(context).toMutableList()
        if (mems.none { it.equals(fact, ignoreCase = true) }) {
            mems.add(fact)
            save(context, mems)
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                val authRepository: com.silica.assistant.core.auth.AuthRepository = org.koin.core.context.GlobalContext.get().get()
                if (authRepository.isLoggedIn()) {
                    authRepository.syncPush()
                }
            } catch (_: Exception) {}
        }
    }

    fun removeMemory(context: Context, keyword: String) {
        val mems = getMemories(context).toMutableList()
        mems.removeAll { it.contains(keyword, ignoreCase = true) }
        save(context, mems)
    }

    fun removeMemoryAt(context: Context, index: Int) {
        val mems = getMemories(context).toMutableList()
        if (index in mems.indices) {
            mems.removeAt(index)
            save(context, mems)
        }
    }

    fun clearAll(context: Context) {
        save(context, emptyList())
    }

    fun buildContext(context: Context): String {
        val mems = getMemories(context)
        if (mems.isEmpty()) return ""
        return mems.joinToString("\n") { "- $it" }
    }

    fun extractForgetCommand(input: String): String? {
        val patterns = listOf(
            Regex("lupakan(?: tentang)? (.+)", RegexOption.IGNORE_CASE),
            Regex("hapus(?: ingatan)?(?: tentang)? (.+)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(input.trim())
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun isClearCommand(input: String): Boolean {
        return input.trim().equals("hapus semua ingatan", ignoreCase = true) ||
                input.trim().equals("lupakan semua", ignoreCase = true)
    }

    fun isListCommand(input: String): Boolean {
        return input.trim().equals("apa yang kamu ingat", ignoreCase = true) ||
                input.trim().equals("tampilkan ingatan", ignoreCase = true) ||
                input.trim().equals("apa saja yang kamu tahu tentang aku", ignoreCase = true)
    }

    private fun save(context: Context, memories: List<String>) {
        val arr = JSONArray()
        for (m in memories) arr.put(m)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MEMORIES, arr.toString())
            .apply()
    }
}
