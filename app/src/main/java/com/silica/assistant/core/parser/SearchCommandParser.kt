package com.silica.assistant.core.parser

object SearchCommandParser {

    private val prefixes =
            listOf(
                    "search ",
                    "cari ",
                    "carikan ",
                    "google ",
                    "searching "
            )

    fun parse(input: String): String? {

        val clean = input.lowercase().trim()

        for (prefix in prefixes) {

            if (clean.startsWith(prefix)) {

                val query = clean.removePrefix(prefix).trim()

                if (query.isNotEmpty()) {
                    return query
                }
            }
        }

        return null
    }
}