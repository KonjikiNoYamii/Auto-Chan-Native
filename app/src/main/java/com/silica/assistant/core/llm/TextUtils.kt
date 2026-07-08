package com.silica.assistant.core.llm

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

fun safeContent(text: String, maxChars: Int = Int.MAX_VALUE): String {
    val lower = text.lowercase()
    val safetyPhrases = listOf("safety", "violence", "harmful", "inappropriate", "blocked", "cannot comment", "tidak bisa", "tidak pantas", "tidak sesuai")
    if (safetyPhrases.count { lower.contains(it) } >= 2 && text.length < 150) return "Hmm, nggak bisa komentar soal itu~"
    val truncated = if (maxChars < Int.MAX_VALUE) limitSentence(text, maxChars) else text
    return truncated
}

fun limitSentence(text: String, maxChars: Int = 160): String {
    if (text.length <= maxChars) return text.trim()
    val cut = text.take(maxChars)
    val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
    return (if (end >= maxChars / 3) cut.substring(0, end + 1) else cut).trim()
}

fun codepointAwareTake(text: String, max: Int): String {
    if (text.length <= max) return text
    val truncated = text.take(max)
    return if (truncated.isNotEmpty() && truncated.last().isHighSurrogate()) text.take(max + 1) else truncated
}

fun markdownToAnnotated(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
