package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.core.llm.model.UserProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MoodManager(private val userProfileDao: UserProfileDao) {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            if (userProfileDao.getProfile() == null) {
                userProfileDao.updateProfile(UserProfileEntity())
            }
        }
    }

    suspend fun getAffinityLevel(): String {
        val profile = userProfileDao.getProfile() ?: return "NEUTRAL"
        return when {
            profile.affinityPoints > 100 -> "LOVING"
            profile.affinityPoints > 50 -> "FRIENDLY"
            profile.affinityPoints < -50 -> "COLD"
            else -> "NEUTRAL"
        }
    }

    fun addAffinity(points: Int) {
        scope.launch {
            userProfileDao.incrementAffinity(points)
        }
    }

    suspend fun getMoodPromptSnippet(): String {
        val level = getAffinityLevel()
        return when (level) {
            "LOVING" -> "Kamu sangat menyayangi User, bicaralah dengan lebih lembut dan perhatian, panggil User 'Tuan' dengan penuh rasa hormat dan kasih sayang."
            "FRIENDLY" -> "Kamu menganggap User teman baik, bicaralah dengan santai tapi tetap sopan."
            "COLD" -> "Kamu sedang kesal atau merasa dingin terhadap User, bicaralah dengan sangat singkat dan ketus."
            else -> "Gunakan kepribadian standar Yami: tenang, stoik, dan sopan."
        }
    }
}
