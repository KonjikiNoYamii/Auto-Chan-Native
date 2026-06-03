package com.silica.assistant.core

import com.silica.assistant.core.command.StringSimilarity
import com.silica.assistant.core.model.CommandResult

object CommandNormalizer {

    private fun normalizeText(input: String): String {
        return input.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }

    private fun tokenize(input: String): List<String> {
        return input.split(" ").filter { it.isNotBlank() }
    }

    private data class FuzzyToken(
        val token: String,
        val isFuzzy: Boolean,
        val original: String
    )

    private fun fuzzyTokenize(inputTokens: List<String>, aliasTokens: List<String>): List<FuzzyToken> {
        val result = mutableListOf<FuzzyToken>()
        val used = mutableSetOf<Int>()

        for (aliasToken in aliasTokens) {
            var matched = false
            for ((i, inputToken) in inputTokens.withIndex()) {
                if (i in used) continue
                if (inputToken == aliasToken) {
                    result.add(FuzzyToken(inputToken, false, aliasToken))
                    used.add(i)
                    matched = true
                    break
                }
            }
            if (!matched) {
                for ((i, inputToken) in inputTokens.withIndex()) {
                    if (i in used) continue
                    if (StringSimilarity.isSimilar(inputToken, aliasToken)) {
                        result.add(FuzzyToken(inputToken, true, aliasToken))
                        used.add(i)
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                result.add(FuzzyToken("", false, aliasToken))
            }
        }

        return result
    }

    private fun score(inputTokens: List<String>, alias: String): Int {
        val aliasTokens = alias.split(" ")
        val fuzzyTokens = fuzzyTokenize(inputTokens, aliasTokens)

        var score = 0

        for (ft in fuzzyTokens) {
            if (ft.token.isNotEmpty()) {
                score += if (ft.isFuzzy) 1 else 2
            }
        }

        val inputJoined = inputTokens.joinToString(" ")
        val aliasJoined = aliasTokens.joinToString(" ")

        if (aliasTokens.size > 1 && inputJoined.contains(aliasJoined)) {
            score += 5
        } else if (aliasTokens.size == 1 && inputJoined == aliasJoined) {
            score += 5
        } else {
            val matchedTokens = fuzzyTokens.count { it.token.isNotEmpty() }
            val totalTokens = aliasTokens.size
            if (totalTokens > 0 && matchedTokens >= totalTokens) {
                score += 3
            }
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

        return if (bestCommand != null && bestScore > 0) {
            CommandResult(command = bestCommand, confidence = bestScore, rawInput = input)
        } else null
    }
}
