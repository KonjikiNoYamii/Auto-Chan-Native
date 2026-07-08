package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MemoryManager : KoinComponent {
    private val userFactDao: UserFactDao by inject()

    suspend fun getMemories(): List<String> {
        val facts = userFactDao.getFactsByPrefix("user_memory_%")
        return facts.map { it.value }
    }

    suspend fun aiAutoExtract(userInput: String) {
        val facts = LlmClient.extractUserFacts(userInput)
        for (fact in facts) {
            addMemory(fact)
        }
    }

    suspend fun addMemory(fact: String) {
        val existing = userFactDao.getFactsByPrefix("user_memory_%")
        if (existing.none { it.value.equals(fact, ignoreCase = true) }) {
            val key = "user_memory_${System.currentTimeMillis()}_${existing.size}"
            userFactDao.insertFact(UserFactEntity(key = key, value = fact))
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val authRepository: com.silica.assistant.core.auth.AuthRepository = org.koin.core.context.GlobalContext.get().get()
                if (authRepository.isLoggedIn()) {
                    authRepository.syncPush()
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun removeMemory(keyword: String) {
        val facts = userFactDao.getFactsByPrefix("user_memory_%")
        for (fact in facts) {
            if (fact.value.contains(keyword, ignoreCase = true)) {
                userFactDao.deleteFactByKey(fact.key)
            }
        }
    }

    suspend fun removeMemoryAt(index: Int) {
        val facts = userFactDao.getFactsByPrefix("user_memory_%")
        if (index in facts.indices) {
            userFactDao.deleteFactByKey(facts[index].key)
        }
    }

    suspend fun clearAll() {
        userFactDao.deleteFactsByPrefix("user_memory_%")
    }

    suspend fun buildContext(): String {
        val mems = getMemories()
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
}
