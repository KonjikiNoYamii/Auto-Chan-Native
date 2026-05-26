package com.silica.assistant.core

object WakeWord {

    private val aliases = listOf(
        "silica",
        "silika",
        "si rika",
        "sirika"
    )

    fun extractCommand(input: String): String? {

        val normalized =
            input.lowercase().trim()

        val matched =
            aliases.firstOrNull {
                normalized.startsWith(it)
            }

        return matched
            ?.let {
                normalized
                    .removePrefix(it)
                    .trim()
            }
    }
}