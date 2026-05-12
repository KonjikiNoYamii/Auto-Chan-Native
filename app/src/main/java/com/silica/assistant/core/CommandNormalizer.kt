package com.silica.assistant.core

object CommandNormalizer {

    fun normalize(input: String): String? {

        val cleanInput = input
            .lowercase()
            .trim()

        var bestCommand: String? = null
        var bestScore = 0

        for ((command, aliases) in CommandAliases.aliases) {

            for (alias in aliases) {

                if (cleanInput.contains(alias)) {

                    val score = alias.length

                    if (score > bestScore) {

                        bestScore = score
                        bestCommand = command
                    }
                }
            }
        }

        return bestCommand
    }
}