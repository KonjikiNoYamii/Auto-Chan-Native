package com.silica.assistant.core.parser

object SearchCommandParser {

    private val prefixes =
            listOf(
                    "search ",
                    "cari ",
                    "carikan ",
                    "cariin ",
                    "google ",
                    "searching ",
                    "tolong cari ",
                    "cari tau ",
                    "cari tahu ",
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