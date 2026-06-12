package com.silica.assistant.core.llm

import com.silica.assistant.R

object EmotionMapper {
    private val tagToEmotion = mapOf(
        "(senyum)" to "happy",
        "(smile)" to "happy",
        "(marah)" to "angry",
        "(malu)" to "blush",
        "(sedih)" to "sad",
    )

    private val emotionToDrawable = mapOf(
        "happy" to R.drawable.yami_happy,
        "angry" to R.drawable.yami_angry,
        "blush" to R.drawable.yami_blush,
        "sad" to R.drawable.mybinik,
    )

    private val emotionToEmotes = mapOf(
        "happy" to listOf("(^_^)", "(^.^)", "(o^.^o)", "(n.n)", "(＾▽＾)", "(✿◠‿◠)", "( ´ ▽ ` )ﾉ", "(b ᵔ▽ᵔ)b", "fufu~", "(´∀｀*)"),
        "angry" to listOf("(#`皿´)", "(ノ ゜Д゜)ノ", "(＃￣0￣)"),
        "blush" to listOf("(///_///)", "(>///<)", "(*^.^*)"),
        "sad" to listOf("(T_T)", "(;-;)", "( ._.)", "(╥﹏╥)", "(｡╯3╰｡)", "(ノ﹏ヽ)"),
        "idle" to listOf("( -_ -)", "( ._ .)", "(─.─)", "(¬_¬)", "( 一_一)", "(￣^￣)", "(─‿─)", "(︶‿︶)", "(￣ω￣)"),
    )

    private val emotionFallback = mapOf(
        "happy" to "...Hmph. (^_^)",
        "angry" to "...Jangan bicara sembarangan. (#`皿´)",
        "blush" to "...Dasar. (///_///)",
        "sad" to "... (T_T)",
    )

    fun getRandomEmote(emotion: String?): String {
        val emotes = emotionToEmotes[emotion] ?: emotionToEmotes["idle"]!!
        return emotes.random()
    }

    fun parseEmotion(text: String): Pair<String, String?> {
        var clean = text.trim()
        for ((tag, emotion) in tagToEmotion) {
            if (clean.contains(tag)) {
                val emote = getRandomEmote(emotion)
                clean = clean.replace(tag, emote).trim()
                if (clean.isBlank() || clean == emote) {
                    return Pair(emotionFallback[emotion] ?: emote, emotion)
                }
                return Pair(clean, emotion)
            }
        }
        return Pair(clean, null)
    }

    fun getDrawable(emotion: String?): Int {
        return emotionToDrawable[emotion] ?: R.drawable.mybinik
    }

    val stickerFilenames: List<String> get() = listOf(
        "yami_happy.jpg",
        "yami_angry.jpg",
        "yami_blush.jpg",
        "yami_sad.jpg",
    )
}
