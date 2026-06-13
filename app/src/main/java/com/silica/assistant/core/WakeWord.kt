package com.silica.assistant.core

import com.silica.assistant.core.command.StringSimilarity

object WakeWord {

    val aliases = listOf(
        "silica",
        "silika",
        "si rika",
        "sirika",
        "sylica",
        "cilika"
    )

    private val singleWordAliases = listOf("silica", "silika", "sirika", "sylica", "cilika")

    private val greetings = listOf("hai ", "hey ", "halo ", "hello ", "hei ", "hi ")

    private fun stripGreeting(normalized: String): String {
        for (greeting in greetings) {
            if (normalized.startsWith(greeting)) {
                return normalized.removePrefix(greeting).trim()
            }
        }
        return normalized
    }

    fun extractCommand(input: String): String? {
        val normalized = stripGreeting(input.lowercase().trim())

        for (alias in aliases) {
            if (normalized.startsWith(alias)) {
                val remainder = normalized.removePrefix(alias).trim()
                return remainder.ifEmpty { null }
            }
        }

        // fuzzy match untuk typo wake word
        val firstWord = normalized.split(" ").firstOrNull() ?: return null
        val matched = StringSimilarity.bestMatch(firstWord, singleWordAliases, threshold = 0.45f)

        if (matched != null) {
            val remainder = normalized.removePrefix(firstWord).trim()
            return remainder.ifEmpty { null }
        }

        return null
    }

    fun isWakeWord(input: String): Boolean {
        val normalized = stripGreeting(input.lowercase().trim())
        for (alias in aliases) {
            if (normalized.startsWith(alias)) return true
        }
        val firstWord = normalized.split(" ").firstOrNull() ?: return false
        return StringSimilarity.bestMatch(firstWord, singleWordAliases, threshold = 0.45f) != null
    }
}
