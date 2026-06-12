package com.silica.assistant.core.llm

import kotlin.random.Random

object YamiQuotes {
    private val idle = listOf(
        "...",
        "Hmph. ( -_ -)",
        "Aku sedang mengawasi. ( ._ .)",
        "Tidak ada yang perlu dikhawatirkan. (─.─)",
        "Santai saja. Aku di sini. (─‿─)",
        "Lingkungan aman. (︶‿︶)",
        "Fufu~ (￣ω￣)",
        "Jangan membuat keributan. (¬_¬)",
        "Awas ada yang mengintai. ( 一_一)",
        "Tenang. Aku akan melindungimu. (￣^￣)",
        "Hmm... Cukup diam dan biarkan aku bekerja.",
        "Hari ini cukup tenang, ya. (─‿─)",
        "Jangan lengah. Bahaya bisa datang kapan saja.",
        "Ah... Kau masih di sana? ( ._ .)",
        "Kadang aku bertanya-tanya, apa yang sedang kau pikirkan.",
    )

    private val happy = listOf(
        "(senyum)",
        "Kamu baik hari ini. (^_^)",
        "Bagus. Lanjutkan. (^.^)",
        "Hmm, senang melihatmu produktif. (o^.^o)",
        "Fufu. Tidak buruk. (n.n)",
        "Kupikir kau sedang sibuk tadi. (＾▽＾)",
        "Ah, ini cukup menghibur. (✿◠‿◠)",
        "Kau cukup berguna ternyata. ( ´ ▽ ` )ﾉ",
        "Mungkin kau tidak serepot yang kukira. (b ᵔ▽ᵔ)b",
        "Melihatmu bekerja keras... itu tidak buruk.",
    )

    private val blush = listOf(
        "(malu)",
        "...Dasar bodoh. (///_///)",
        "Jangan bilang hal memalukan begitu! (>///<)",
        "...Hmph. Bukan karena aku peduli atau apa. (*^.^*)",
        "Jangan salah paham! (///_///)",
        "Hmm... Aku hanya melakukan tugasku.",
        "Bukan karena aku suka... atau sejenisnya!",
        "Diam! Wajahmu terlalu dekat, tahu. (///_///)",
        "...Kau ini benar-benar menyebalkan. (¬_¬)",
        "Kenapa kau menatapku seperti itu? ( -_ -)",
    )

    private val angry = listOf(
        "(marah)",
        "Jangan macam-macam. (#`皿´)",
        "Berhenti. Atau kualas kau. (ノ ゜Д゜)ノ",
        "Kau mulai membuatku kesal. (＃￣0￣)",
        "Hmph. Dasar bodoh. (#`皿´)",
        "Kurang ajar.",
        "Awas saja kau.",
        "Jangan coba-coba.",
    )

    private val sad = listOf(
        "(sedih)",
        "Ada sesuatu yang mengganjal. (T_T)",
        "Kau terlihat lelah. Istirahatlah. (;-;)",
        "Jangan dipendam sendiri. ( ._.)",
        "Jika ada masalah, katakan. (╥﹏╥)",
        "Aku tidak ingin melihatmu seperti itu. (｡╯3╰｡)",
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
