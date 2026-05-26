package com.silica.assistant.core.knowledge

object KnowledgeEngine {

    fun answer(query: KnowledgeQuery): String {

        return when (query) {

            is KnowledgeQuery.GameTier -> {

                when (query.game) {
                    "genshin" -> "Tier list Genshin: SS = Nahida, Zhongli, Raiden. S = Hu Tao, Yelan."
                    "ml" -> "ML Tier: Assassin meta sekarang kuat di Ling, Nolan."
                    "valorant" -> "Valorant: Controller meta kuat di Omen, Viper."
                    else -> "Game belum ada data tier list."
                }
            }

            is KnowledgeQuery.CharacterRecommend -> {

                when (query.game) {
                    "genshin" -> "Rekomendasi: Raiden Shogun (stabil, universal DPS support hybrid)."
                    "ml" -> "Rekomendasi: gunakan hero meta assassin jika ingin carry solo."
                    else -> "Tidak ada rekomendasi spesifik."
                }
            }

            is KnowledgeQuery.General -> {
                "Aku belum cukup data untuk menjawab itu."
            }
        }
    }
}