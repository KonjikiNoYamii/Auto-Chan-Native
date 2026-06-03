package com.silica.assistant.core.llm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MemoryManager {
    private const val PREFS_NAME = "llm_memories"
    private const val KEY_MEMORIES = "memories"

    private val autoPatterns = listOf(
        Regex("(?:nama(?:ku| saya| gw)?|panggil(?: aku| saya)?) (?:adalah|itu|yaitu) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:nama(?:ku| saya| gw)?) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:tinggal|rumah|domisili) (?:di|nya) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:dari|berasal) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:suka|gemar|hobi) (.+)", RegexOption.IGNORE_CASE),
        Regex("hobi(?:ku| saya)? (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:umur|usia)(?:ku| saya)? (?:\\d+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:berusia|berumur) (?:\\d+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:kerja|bekerja|kerjaan) (?:sebagai|di|jadi) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:adalah|seorang) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:punya|memiliki) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:warna|makanan|minuman|film|musik|game|anime) (?:favorit|kesukaan)(?:ku| saya)? (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:suka|sangat suka|gemar) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:gak|tidak|nggak) (?:suka|gemar) (.+)", RegexOption.IGNORE_CASE),
        Regex("(?:aku|saya|gw) (?:lagi|sedang) (?:belajar|kuliah|sekolah) (.+)", RegexOption.IGNORE_CASE),
    )

    fun getMemories(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEMORIES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun addMemory(context: Context, fact: String) {
        val mems = getMemories(context).toMutableList()
        if (mems.none { it.equals(fact, ignoreCase = true) }) {
            mems.add(fact)
            save(context, mems)
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

    fun autoExtract(userInput: String): List<String> {
        val trimmed = userInput.trim()
        val facts = mutableListOf<String>()
        for (pattern in autoPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                var fact = match.groupValues[1].trim().removeSuffix(".").removeSuffix(",")
                val full = match.groupValues[0]
                // clean up: if match is whole message, use template
                val clean = when {
                    full.startsWith("nama") || full.contains("nama") -> "Namanya $fact"
                    full.contains("suka") || full.contains("gemar") -> "Suka $fact"
                    full.contains("tinggal") || full.contains("rumah") || full.contains("domisili") || full.contains("berasal") -> "Tinggal di $fact"
                    full.contains("kerja") || full.contains("bekerja") -> "Bekerja sebagai $fact"
                    full.contains("hobi") -> "Hobi $fact"
                    full.contains("punya") || full.contains("memiliki") -> "Punya $fact"
                    full.contains("adalah") && !full.contains("suka") -> "$fact"
                    else -> fact
                }
                facts.add(clean)
            }
        }
        return facts.distinct()
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
