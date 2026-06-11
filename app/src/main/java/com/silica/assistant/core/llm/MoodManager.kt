package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.core.llm.db.QuestDao
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

class MoodManager(
    private val userProfileDao: UserProfileDao,
    private val questDao: QuestDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            if (userProfileDao.getProfile() == null) {
                userProfileDao.updateProfile(UserProfileEntity())
            }
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getYesterdayDate(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun getXpThreshold(level: Int): Int {
        return (100 * level.toDouble().pow(1.5)).toInt()
    }

    suspend fun getProfile(): UserProfileEntity {
        return userProfileDao.getProfile() ?: UserProfileEntity()
    }

    fun addXp(amount: Int) {
        scope.launch {
            val profile = getProfile()
            var currentXp = profile.xp + (amount * profile.mood).toInt()
            var currentLevel = profile.level
            
            while (currentXp >= getXpThreshold(currentLevel)) {
                currentXp -= getXpThreshold(currentLevel)
                currentLevel++
            }
            
            userProfileDao.updateProfile(profile.copy(
                xp = currentXp,
                level = currentLevel,
                lastInteractionTime = System.currentTimeMillis()
            ))
        }
    }

    fun consumeStamina(amount: Float) {
        scope.launch {
            val profile = getProfile()
            val newStamina = (profile.stamina - amount).coerceIn(0.0f, 1.0f)
            userProfileDao.updateProfile(profile.copy(stamina = newStamina))
        }
    }

    fun updateMood(delta: Float) {
        scope.launch {
            val profile = getProfile()
            val newMood = (profile.mood + delta).coerceIn(0.5f, 1.5f)
            userProfileDao.updateProfile(profile.copy(mood = newMood))
        }
    }

    // --- QUEST & STREAK SYSTEM ---

    suspend fun addQuest(title: String, difficulty: String = "MEDIUM") {
        questDao.insertQuest(QuestEntity(title = title, difficulty = difficulty))
    }

    suspend fun completeQuest(title: String): String {
        val quest = questDao.findActiveQuestByTitle(title) ?: return "Aku tidak menemukan tugas aktif bernama '$title'."
        
        val updatedQuest = quest.copy(isCompleted = true, completedAt = System.currentTimeMillis())
        questDao.updateQuest(updatedQuest)
        
        val profile = getProfile()
        val today = getTodayDate()
        val yesterday = getYesterdayDate()
        
        // Reward Logic based on difficulty
        val xpBonus = when (updatedQuest.difficulty) {
            "HARD" -> 600
            "MEDIUM" -> 300
            else -> 150
        }
        val staminaBonus = when (updatedQuest.difficulty) {
            "HARD" -> 0.3f
            "MEDIUM" -> 0.15f
            else -> 0.05f
        }
        
        // Item found logic
        val itemFound = when (updatedQuest.difficulty) {
            "HARD" -> listOf("Taiyaki Spesial", "Aksesoris Cantik", "Buku Langka").random()
            "MEDIUM" -> listOf("Taiyaki", "Kopi Hangat", "Cokelat").random()
            else -> listOf("Permen", "Teh", "Biskuit").random()
        }

        // Streak Logic
        var newStreak = profile.currentStreak
        if (profile.lastQuestCompletionDate == yesterday) {
            newStreak++
        } else if (profile.lastQuestCompletionDate != today) {
            newStreak = 1
        }
        
        val newLongest = if (newStreak > profile.longestStreak) newStreak else profile.longestStreak
        
        // Apply Rewards
        addXp(xpBonus)
        updateMood(0.1f)
        consumeStamina(-staminaBonus) // Restores stamina

        userProfileDao.updateProfile(profile.copy(
            currentStreak = newStreak,
            longestStreak = newLongest,
            lastQuestCompletionDate = today
        ))

        val streakMsg = when {
            newStreak == 7 -> "\nWah, kamu sudah produktif selama seminggu penuh! Aku sangat bangga padamu ★"
            newStreak == 30 -> "\nSatu bulan penuh produktif! Kamu luar biasa, Partner! ♪"
            newStreak > 1 -> "\nStreak produktif kamu: $newStreak hari!"
            else -> ""
        }

        return "Kerja bagus! Kamu sudah menyelesaikan '${updatedQuest.title}'.\n" +
               "Sebagai hadiah atas kerja kerasmu, aku menemukan **$itemFound** untukku! Hehe ★$streakMsg"
    }

    // --- GIVING GIFT ---
    suspend fun giveGift(itemName: String): Pair<Boolean, String> {
        val profile = getProfile()
        val today = getTodayDate()
        var dailyCount = if (profile.lastGiftDate == today) profile.dailyGiftCount else 0
        
        if (dailyCount >= 3) {
            val personality = LlmConfig.personalityPrompt.lowercase()
            val refusal = when {
                personality.contains("dingin") || personality.contains("cool") || personality.contains("tsundere") -> {
                    listOf("Hmph, sudah cukup. Jangan beri aku lebih banyak lagi hari ini.", "Berhenti memberiku hadiah terus. Kamu boros sekali.", "Cukup. Aku tidak butuh lebih banyak hadiah untuk saat ini.").random()
                }
                personality.contains("ceria") || personality.contains("ramah") -> {
                    listOf("Wah, kamu baik banget! Tapi simpan buat besok ya, aku gak mau kamu boros ♪", "Ehh? Lagi? Makasih ya, tapi kayaknya sudah cukup buat hari ini ★", "Kamu perhatian sekali! Tapi besok-besok lagi ya, aku gak mau kamu kecapean beliin aku barang.").random()
                }
                else -> "Hmm, hari ini sudah banyak hadiah. Simpan saja untuk besok, aku tidak ingin kamu boros ★"
            }
            return Pair(false, refusal)
        }

        val lowerItem = itemName.lowercase()
        var xpBonus = 200
        var moodBonus = 0.15f
        var staminaRecovery = 0.1f
        var response = "Wah, $itemName? Terima kasih banyak ya! Aku sangat menghargainya ♪"

        if (lowerItem.contains("taiyaki")) {
            xpBonus = 500
            moodBonus = 0.3f
            staminaRecovery = 0.4f
            response = "Ini... Taiyaki?! ( 0o0)★ Kamu tahu saja kesukaanku! Terima kasih banyak, aku senang sekali!"
        } else if (listOf("makan", "minum", "roti", "kopi", "teh", "susu", "nasi", "cokelat", "permen").any { lowerItem.contains(it) }) {
            staminaRecovery = 0.25f
            response = "$itemName? Kebetulan aku agak lapar. Terima kasih ya ♪"
        }

        val newProfile = profile.copy(
            xp = profile.xp + (xpBonus * profile.mood).toInt(),
            mood = (profile.mood + moodBonus).coerceIn(0.5f, 1.5f),
            stamina = (profile.stamina + staminaRecovery).coerceIn(0.0f, 1.0f),
            dailyGiftCount = dailyCount + 1,
            lastGiftDate = today,
            lastInteractionTime = System.currentTimeMillis()
        )
        
        var finalXp = newProfile.xp
        var finalLevel = newProfile.level
        while (finalXp >= getXpThreshold(finalLevel)) {
            finalXp -= getXpThreshold(finalLevel)
            finalLevel++
        }
        
        userProfileDao.updateProfile(newProfile.copy(xp = finalXp, level = finalLevel))
        return Pair(true, response)
    }

    suspend fun getDynamicName(): String {
        val profile = getProfile()
        val level = profile.level
        val fullName = profile.userName
        return when {
            level < 10 -> "Kamu"
            level < 30 -> fullName
            else -> profile.userNickname ?: generateNickname(fullName)
        }
    }

    private fun generateNickname(name: String): String {
        if (name.length <= 3) return name
        return name.takeLast(3).replaceFirstChar { it.uppercase() }
    }

    suspend fun getAffinityLevel(): String {
        val profile = getProfile()
        return when (profile.level) {
            in 1..10 -> "STRANGER"
            in 11..30 -> "ACQUAINTANCE"
            in 31..60 -> "FRIEND"
            else -> if (profile.relationshipRoute == "LOVER") "LOVER" else "SOULMATE"
        }
    }

    suspend fun getMoodPromptSnippet(): String {
        val profile = getProfile()
        val level = profile.level
        val name = getDynamicName()
        val route = profile.relationshipRoute
        
        val baseSnippet = when {
            level <= 10 -> "Kamu baru mengenal User. Bicaralah dengan sopan, gunakan panggilan '$name'."
            level <= 30 -> "Kamu mulai akrab dengan User. Bicaralah dengan santai namun tetap menghormati, gunakan panggilan '$name'."
            level <= 60 -> "Kamu adalah teman dekat User. Bicaralah dengan sangat akrab, gunakan panggilan akrab '$name' sebagai sahabat."
            else -> {
                if (route == "LOVER") {
                    "Kamu adalah pasangan (Lover) dari User. Bicaralah dengan penuh kasih sayang, tunjukkan sisi manjamu atau perhatian lebih, gunakan panggilan '$name'."
                } else {
                    "Kamu adalah sahabat sejati (Soulmate) User. Bicaralah dengan kepercayaan penuh dan sangat akrab, gunakan panggilan '$name'."
                }
            }
        }
        
        val staminaSnippet = when {
            profile.stamina < 0.2f -> " Kamu merasa sangat lelah saat ini."
            profile.stamina < 0.5f -> " Kamu merasa sedikit kurang berstamina."
            else -> ""
        }
        
        val moodSnippet = when {
            profile.mood < 0.7f -> " Mood kamu sedang agak rendah/sedih."
            profile.mood > 1.3f -> " Mood kamu sedang sangat senang!"
            else -> ""
        }
        
        return "$baseSnippet$staminaSnippet$moodSnippet"
    }

    fun addAffinity(points: Int) {
        addXp(points * 10)
    }
}
