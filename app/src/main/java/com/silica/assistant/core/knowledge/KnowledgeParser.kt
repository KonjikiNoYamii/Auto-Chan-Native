package com.silica.assistant.core.knowledge

object KnowledgeParser {

    fun parse(input: String): KnowledgeQuery? {

        val text = input.lowercase()

        return when {

            text.contains("tier") || text.contains("tier list") ->
                KnowledgeQuery.GameTier(extractGame(text))

            text.contains("siapa karakter") ||
            text.contains("character terbaik") ->
                KnowledgeQuery.CharacterRecommend(extractGame(text))

            text.contains("siapa") ->
                KnowledgeQuery.General(text)

            else -> null
        }
    }

    private fun extractGame(text: String): String {

        return when {
            text.contains("genshin") -> "genshin"
            text.contains("ml") || text.contains("mobile legends") -> "ml"
            text.contains("valorant") -> "valorant"
            else -> "unknown"
        }
    }
}