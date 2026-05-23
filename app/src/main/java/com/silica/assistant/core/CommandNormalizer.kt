package com.silica.assistant.core

import com.silica.assistant.core.model.CommandResult

object CommandNormalizer {

    private fun normalizeText(input: String): String {
        return input.lowercase()
                .trim()
                .replace(Regex("[^a-z0-9\\s]"), "") // hapus simbol
                .replace(Regex("\\s+"), " ") // rapikan spasi
    }

    private fun tokenize(input: String): List<String> {
        return input.split(" ").filter { it.isNotBlank() }
    }

    private fun score(inputTokens: List<String>, alias: String): Int {
        val aliasTokens = alias.split(" ")

        var score = 0

        for (token in aliasTokens) {
            if (inputTokens.contains(token)) {
                score += 2
            }
        }

        // bonus jika alias muncul full phrase
        if (inputTokens.joinToString(" ").contains(alias)) {
            score += 5
        }

        return score
    }

    fun normalize(input: String): CommandResult? {

        val clean = normalizeText(input)
        val tokens = tokenize(clean)

        var bestCommand: String? = null
        var bestScore = 0

        for ((command, aliases) in CommandAliases.aliases) {

            for (alias in aliases) {

                val aliasClean = normalizeText(alias)
                val currentScore = score(tokens, aliasClean)

                if (currentScore > bestScore) {
                    bestScore = currentScore
                    bestCommand = command
                }
            }
        }

        return if (bestCommand != null) {
            CommandResult(command = bestCommand, confidence = bestScore, rawInput = input)
        } else null
    }
}
