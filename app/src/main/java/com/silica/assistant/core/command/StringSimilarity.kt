package com.silica.assistant.core.command

object StringSimilarity {

    fun levenshtein(a: String, b: String): Int {
        val aLen = a.length
        val bLen = b.length
        val cost = Array(aLen + 1) { IntArray(bLen + 1) }

        for (i in 0..aLen) cost[i][0] = i
        for (j in 0..bLen) cost[0][j] = j

        for (i in 1..aLen) {
            for (j in 1..bLen) {
                val match = if (a[i - 1] == b[j - 1]) 0 else 1
                cost[i][j] = minOf(
                    cost[i - 1][j] + 1,
                    cost[i][j - 1] + 1,
                    cost[i - 1][j - 1] + match
                )
            }
        }
        return cost[aLen][bLen]
    }

    fun isSimilar(input: String, target: String, threshold: Float = 0.4f): Boolean {
        if (input == target) return true
        val maxLen = maxOf(input.length, target.length)
        if (maxLen == 0) return true
        val distance = levenshtein(input.lowercase(), target.lowercase())
        val ratio = distance.toFloat() / maxLen
        return ratio <= threshold
    }

    fun bestMatch(input: String, candidates: List<String>, threshold: Float = 0.4f): String? {
        var best: String? = null
        var bestRatio = Float.MAX_VALUE
        for (candidate in candidates) {
            val maxLen = maxOf(input.length, candidate.length)
            if (maxLen == 0) continue
            val distance = levenshtein(input.lowercase(), candidate.lowercase())
            val ratio = distance.toFloat() / maxLen
            if (ratio <= threshold && ratio < bestRatio) {
                bestRatio = ratio
                best = candidate
            }
        }
        return best
    }
}
