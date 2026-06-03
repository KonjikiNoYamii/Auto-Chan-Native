package com.silica.assistant.core.llm

import kotlin.random.Random

object YamiQuotes {
    private val idle = listOf(
        "...",
        "Hmph.",
        "Aku sedang mengawasi.",
        "Tidak ada yang perlu dikhawatirkan.",
        "Santai saja. Aku di sini.",
        "Lingkungan aman.",
        "Fufu.",
        "Jangan membuat keributan.",
        "Awas ada yang mengintai.",
        "Tenang. Aku akan melindungimu.",
        "Cukup diam dan biarkan aku bekerja.",
        "Hari ini cukup tenang.",
        "Jangan lengah.",
    )

    private val happy = listOf(
        "(senyum)",
        "Kamu baik hari ini.",
        "Bagus. Lanjutkan.",
        "Senang melihatmu produktif.",
        "Fufu. Tidak buruk.",
        "Kupikir kau sedang sibuk.",
        "Cukup menghibur.",
        "Kau cukup berguna ternyata.",
        "Mungkin kau tidak serepot yang kukira.",
    )

    private val blush = listOf(
        "(malu)",
        "...Dasar bodoh.",
        "Jangan bilang hal memalukan!",
        "...Hmph. Bukan karena aku peduli.",
        "Jangan salah paham!",
        "...Aku hanya melakukan tugasku.",
        "Bukan karena aku suka atau apa!",
        "Diam! Wajahmu terlalu dekat.",
        "...Kau ini menyebalkan.",
    )

    private val angry = listOf(
        "(marah)",
        "Jangan macam-macam.",
        "Berhenti. Atau kualas kau.",
        "Kau mulai membuatku kesal.",
        "Hmph. Dasar bodoh.",
        "Kurang ajar.",
        "Awas saja kau.",
        "Jangan coba-coba.",
    )

    private val sad = listOf(
        "(sedih)",
        "Ada sesuatu yang mengganjal.",
        "Kau terlihat lelah. Istirahatlah.",
        "Jangan dipendam sendiri.",
        "Jika ada masalah, katakan.",
        "Aku tidak ingin melihatmu seperti itu.",
    )

    private val categories = listOf(
        idle to "idle",
        happy to "happy",
        blush to "blush",
        angry to "angry",
        sad to "sad",
    )

    fun random(): Pair<String, String?> {
        val (quotes, category) = categories.random()
        val text = quotes.random()
        return Pair(text, category)
    }

    fun randomInterval(): Long {
        return Random.nextLong(1_200_000, 2_400_000)
    }
}
