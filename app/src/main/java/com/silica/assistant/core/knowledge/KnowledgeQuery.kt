package com.silica.assistant.core.knowledge

sealed class KnowledgeQuery {

    data class GameTier(val game: String) : KnowledgeQuery()

    data class CharacterRecommend(
        val game: String
    ) : KnowledgeQuery()

    data class General(val text: String) : KnowledgeQuery()
}