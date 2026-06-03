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

    private val emotionFallback = mapOf(
        "happy" to "...Hmph.",
        "angry" to "...Jangan bicara sembarangan.",
        "blush" to "...Dasar.",
        "sad" to "...",
    )

    fun parseEmotion(text: String): Pair<String, String?> {
        var clean = text.trim()
        for ((tag, emotion) in tagToEmotion) {
            if (clean.endsWith(tag)) {
                clean = clean.removeSuffix(tag).trim()
                if (clean.isBlank()) {
                    return Pair(emotionFallback[emotion] ?: "...", emotion)
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
