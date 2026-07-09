package com.silica.assistant.core.llm

import android.util.Log
import com.silica.assistant.core.auth.AuthRepository
import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.core.llm.db.QuestDao
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.core.llm.LlmConfig
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.system.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

class MoodManager(
    private val userProfileDao: UserProfileDao,
    private val questDao: QuestDao,
    private val authRepository: AuthRepository,
    private val achievementManager: AchievementManager,
    private val activityDetector: com.silica.assistant.core.ActivityDetector
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val questCategoryMapping = mapOf(
        "ngoding" to listOf("com.termux", "com.aide.ui", "com.github.android", "org.goffi.termux", "com.dualscreen.terminal"),
        "coding" to listOf("com.termux", "com.aide.ui", "com.github.android"),
        "belajar" to listOf("com.google.android.apps.docs", "com.adobe.reader", "com.duolingo", "com.sololearn", "org.khanacademy.android"),
        "nonton" to listOf("com.google.android.youtube", "com.netflix.mediaclient", "com.disney.disneyplus", "com.mxtech.videoplayer.ad"),
        "kerja" to listOf("com.microsoft.office.outlook", "com.slack", "com.google.android.apps.meetings", "com.microsoft.teams"),
        "olahraga" to listOf("com.google.android.apps.fitness", "com.strava", "com.fitbit.FitbitMobile")
    )

    enum class AbsenceLevel { NONE, SHORT, MEDIUM, LONG }

    init {
        scope.launch {
            if (userProfileDao.getProfile() == null) {
                userProfileDao.updateProfile(UserProfileEntity())
            }
            achievementManager.initAchievements()
            resetDailyQuests()
        }
    }

    private fun isContextEligible(questTitle: String): Boolean {
        val lowerTitle = questTitle.lowercase()
        for ((category, packages) in questCategoryMapping) {
            if (lowerTitle.contains(category)) {
                // Check current foreground app first
                val currentApp = activityDetector.getForegroundApp()
                if (currentApp != null && packages.contains(currentApp)) return true
                
                // Then check recent history (3 hours)
                for (pkg in packages) {
                    if (activityDetector.hasUsedAppRecently(pkg)) return true
                }
            }
        }
        return false
    }

    suspend fun verifyWithVision(questTitle: String, imageJpeg: ByteArray): Pair<Boolean, String> {
        val prompt = """
            User mengirim foto ini sebagai bukti menyelesaikan tugas: "$questTitle".
            Analisa foto tersebut. Apakah isinya sesuai dengan tugas tersebut?
            Jika SESUAI: Balas HANYA dengan kata "VALID" diikuti alasan singkat dan emosi (senyum).
            Jika TIDAK SESUAI: Balas HANYA dengan kata "FAKE" diikuti alasan kenapa itu palsu dan emosi (marah).
            Karaktermu: Yami, assassin yang teliti dan tidak suka kebohongan.
        """.trimIndent()
        
        val result = LlmClient.describeScreen("Bukti Quest", prompt, imageJpeg) ?: return Pair(false, "Hmm, aku tidak bisa melihat gambarnya dengan jelas. (¬_¬)")
        
        val isValid = result.startsWith("VALID", ignoreCase = true)
        if (isValid) {
            // Manual verification success
            val quest = questDao.findActiveQuestByTitle(questTitle) ?: questDao.getCompletedQuests().first().find { it.title == questTitle }
            if (quest != null) {
                val updatedQuest = quest.copy(isEligible = true)
                questDao.updateQuest(updatedQuest)
                
                val profile = getProfile()
                userProfileDao.updateProfile(profile.copy(
                    verifiedQuestCount = profile.verifiedQuestCount + 1,
                    affinityPoints = profile.affinityPoints + 5 // Bonus points for evidence
                ))
                triggerAutoSync()
            }
        } else if (result.startsWith("FAKE", ignoreCase = true)) {
            // User lied!
            val profile = getProfile()
            userProfileDao.updateProfile(profile.copy(
                affinityPoints = (profile.affinityPoints - 10).coerceAtLeast(0),
                mood = (profile.mood - 0.2f).coerceIn(0.5f, 1.5f)
            ))
            triggerAutoSync()
        }
        
        return Pair(isValid, result.replace("VALID", "").replace("FAKE", "").trim())
    }

    private suspend fun resetDailyQuests() {
        val today = getTodayDate()
        val allCompleted = questDao.getCompletedQuests().first()
        
        allCompleted.forEach { quest ->
            val completionDate = quest.completedAt?.let { 
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) 
            }
            
            // Jika tugas selesai bukan hari ini (kemarin atau sebelumnya), reset jadi aktif
            if (completionDate != null && completionDate != today) {
                questDao.updateQuest(quest.copy(
                    isCompleted = false,
                    completedAt = null
                ))
            }
        }
    }

    private fun triggerAutoSync() {
        scope.launch {
            if (authRepository.isLoggedIn()) {
                val result = authRepository.syncPush()
                if (result.isFailure) {
                    Log.e("MoodManager", "Sync gagal: ${result.exceptionOrNull()?.message}")
                }
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

    fun getXpThresholdPublic(level: Int): Int = getXpThreshold(level)

    // ── Absence System ──

    private fun calculateAbsenceLevel(lastSeen: Long): AbsenceLevel {
        val hours = (System.currentTimeMillis() - lastSeen) / (1000 * 60 * 60)
        return when {
            hours < 24 -> AbsenceLevel.NONE
            hours < 72 -> AbsenceLevel.SHORT
            hours < 168 -> AbsenceLevel.MEDIUM
            else -> AbsenceLevel.LONG
        }
    }

    suspend fun getAbsenceContext(): String {
        val profile = getProfile()
        val absence = calculateAbsenceLevel(profile.lastInteractionTime)
        if (absence == AbsenceLevel.NONE) return ""

        val affinity = profile.affinityPoints
        val tierIndex = getAffinityTier(affinity)
        val tierName = affinityTierNames.getOrElse(tierIndex) { "Soulmate" }

        val duration = when (absence) {
            AbsenceLevel.SHORT -> "1-3 hari"
            AbsenceLevel.MEDIUM -> "3-7 hari"
            AbsenceLevel.LONG -> "lebih dari seminggu"
            else -> ""
        }

        val emotion = when {
            tierIndex <= 1 -> "tidak terlalu peduli — kenalan biasa"
            tierIndex <= 2 -> "sedikit heran, tapi tidak terlalu memikirkannya"
            tierIndex <= 3 -> "cukup terasa, tapi tidak akan mengungkapkannya secara langsung"
            tierIndex <= 4 -> "agak khawatir, tapi topeng dinginmu masih terjaga"
            tierIndex <= 5 -> "khawatir dan sedikit kesal, tapi kamu terlalu dingin untuk mengakuinya"
            tierIndex <= 6 -> "cemas campur kesal — ingin marah tapi lebih khawatir"
            tierIndex <= 7 -> "sangat cemas. topeng dinginmu mulai retak. kamu hampir tidak bisa menyembunyikannya"
            else -> "sangat merindukannya. hatimu terasa kosong. ini pertama kalinya kamu merasa begini"
        }

        return "[Catatan: User tidak ada kabar selama $duration. Di level '$tierName', kamu merasa $emotion. Jangan berlebihan, ekspresikan secara alami sesuai kepribadianmu yang pendiam.]"
    }

    suspend fun markUserReachedOut() {
        val profile = getProfile()
        userProfileDao.updateProfile(profile.copy(lastInteractionTime = System.currentTimeMillis()))
        triggerAutoSync()
    }

    suspend fun getProfile(): UserProfileEntity {
        return userProfileDao.getProfile() ?: UserProfileEntity()
    }

    fun addXp(amount: Int) {
        scope.launch {
            val profile = getProfile()
            var currentXp = profile.xp + (amount * profile.mood).toInt()
            var currentLevel = profile.level
            var leveledUp = false
            
            while (currentXp >= getXpThreshold(currentLevel)) {
                currentXp -= getXpThreshold(currentLevel)
                currentLevel++
                leveledUp = true
            }
            
            if (leveledUp) {
                SoundManager.playSound("level_up")
            }
            
            userProfileDao.updateProfile(profile.copy(
                xp = currentXp,
                level = currentLevel,
                lastInteractionTime = System.currentTimeMillis()
            ))
            triggerAutoSync()
        }
    }

    fun consumeStamina(amount: Float) {
        scope.launch {
            val profile = getProfile()
            val newStamina = (profile.stamina - amount).coerceIn(0.0f, 1.0f)
            userProfileDao.updateProfile(profile.copy(stamina = newStamina))
            triggerAutoSync()
        }
    }

    fun updateMood(delta: Float) {
        scope.launch {
            val profile = getProfile()
            val newMood = (profile.mood + delta).coerceIn(0.5f, 1.5f)
            userProfileDao.updateProfile(profile.copy(mood = newMood))
            triggerAutoSync()
        }
    }

    // --- QUEST & STREAK SYSTEM ---

    suspend fun addQuest(title: String, difficulty: String = "MEDIUM") {
        questDao.insertQuest(QuestEntity(title = title, difficulty = difficulty))
        triggerAutoSync()
    }

    suspend fun completeQuest(title: String): String {
        val quest = questDao.findActiveQuestByTitle(title) ?: return "Aku tidak menemukan tugas aktif bernama '$title'."
        
        val eligible = isContextEligible(title)
        val updatedQuest = quest.copy(isCompleted = true, completedAt = System.currentTimeMillis(), isEligible = eligible)
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
        
        // Update Inventory
        val currentInventory = if (profile.inventory.isBlank()) mutableListOf<String>() else profile.inventory.split(",").toMutableList()
        currentInventory.add(itemFound)
        val newInventoryString = currentInventory.joinToString(",")

        // Apply Rewards to Profile
        var currentXp = profile.xp + xpBonus
        var currentLevel = profile.level
        var leveledUp = false
        
        while (currentXp >= getXpThreshold(currentLevel)) {
            currentXp -= getXpThreshold(currentLevel)
            currentLevel++
            leveledUp = true
        }

        if (leveledUp) {
            SoundManager.playLevelUp()
        }

        MemoryManager.logSharedMemory("Selesaikan quest '${updatedQuest.title}' (${updatedQuest.difficulty}) — dapat $xpBonus XP + $itemFound", "quest")

        val oldAffinity = profile.affinityPoints
        val affinityBonus = xpBonus / 40

        userProfileDao.updateProfile(profile.copy(
            xp = currentXp,
            level = currentLevel,
            currentStreak = newStreak,
            longestStreak = newLongest,
            lastQuestCompletionDate = today,
            inventory = newInventoryString,
            affinityPoints = oldAffinity + affinityBonus,
            mood = (profile.mood + 0.1f).coerceIn(0.5f, 1.5f),
            stamina = (profile.stamina + 0.1f).coerceIn(0.0f, 1.0f),
            totalQuestCount = profile.totalQuestCount + 1,
            verifiedQuestCount = profile.verifiedQuestCount + if (eligible) 1 else 0
        ))
        triggerAutoSync()
        checkAffinityLevelUp(oldAffinity, oldAffinity + affinityBonus)
        
        val newProfile = getProfile()
        val allCompleted = questDao.getCompletedQuests().first()
        val hardCount = allCompleted.count { it.difficulty == "HARD" }
        achievementManager.checkAchievements(newProfile, allCompleted.size, hardCount)

        val verifyMsg = if (eligible) "\n(☆▽☆) **QUEST TERVERIFIKASI!** Aku mendeteksi aktivitasmu. ♪" else "\n(¬_¬) **QUEST MANUAL.** Aku tidak mendeteksi aktivitas aplikasi terkait."
        
        val levelMsg = if (leveledUp) "\n(＾▽＾) **LEVEL UP!** Kamu sekarang Level $currentLevel! ♪" else ""
        val streakMsg = when {
            newStreak == 7 -> "\nWah, kamu sudah produktif selama seminggu penuh! Aku sangat bangga padamu ♪"
            newStreak == 30 -> "\nSatu bulan penuh produktif! Kamu luar biasa, Partner! ♪"
            newStreak > 1 -> "\nStreak produktif kamu: $newStreak hari!"
            else -> ""
        }

        return "Kerja bagus! Kamu sudah menyelesaikan '${updatedQuest.title}'.\n" +
               "Kamu mendapatkan **$xpBonus XP** dan menemukan **$itemFound**! ♪$verifyMsg$levelMsg$streakMsg"
    }

    suspend fun getInventory(): List<String> {
        val profile = getProfile()
        if (profile.inventory.isBlank()) return emptyList()
        return profile.inventory.split(",")
    }

    suspend fun removeItemFromInventory(item: String) {
        val profile = getProfile()
        val items = profile.inventory.split(",").toMutableList()
        val index = items.indexOf(item)
        if (index != -1) {
            items.removeAt(index)
            userProfileDao.updateProfile(profile.copy(inventory = items.joinToString(",")))
            triggerAutoSync()
        }
    }

    fun giveGiftAffinity(itemName: String) {
        scope.launch {
            val (success, _) = giveGift(itemName)
            if (success) {
                removeItemFromInventory(itemName)
            }
        }
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
                    listOf("Wah, kamu baik banget! Tapi simpan buat besok ya, aku gak mau kamu boros ♪", "Ehh? Lagi? Makasih ya, tapi kayaknya sudah cukup buat hari ini ♪", "Kamu perhatian sekali! Tapi besok-besok lagi ya, aku gak mau kamu kecapean beliin aku barang.").random()
                }
                else -> "Hmm, hari ini sudah banyak hadiah. Simpan saja untuk besok, aku tidak ingin kamu boros ♪"
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
            response = "Ini... Taiyaki?! ( 0o0)♪ Kamu tahu saja kesukaanku! Terima kasih banyak, aku senang sekali!"
        } else if (listOf("makan", "minum", "roti", "kopi", "teh", "susu", "nasi", "cokelat", "permen").any { lowerItem.contains(it) }) {
            staminaRecovery = 0.25f
            response = "$itemName? Kebetulan aku agak lapar. Terima kasih ya ♪"
        }

        val newProfile = profile.copy(
            xp = profile.xp + (xpBonus * profile.mood).toInt(),
            affinityPoints = profile.affinityPoints + (xpBonus / 40),
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
        
        MemoryManager.logSharedMemory("Terima hadiah $itemName dari user ♪", "gift")

        val oldAffinity = profile.affinityPoints
        val affinityBonus = xpBonus / 40
        val newAffinity = oldAffinity + affinityBonus

        userProfileDao.updateProfile(newProfile.copy(
            xp = finalXp,
            level = finalLevel,
            affinityPoints = newAffinity
        ))
        triggerAutoSync()
        checkAffinityLevelUp(oldAffinity, newAffinity)
        
        val finalProfile = getProfile()
        val allCompleted = questDao.getCompletedQuests().first()
        achievementManager.checkAchievements(finalProfile, allCompleted.size, allCompleted.count { it.difficulty == "HARD" })
        
        return Pair(true, response)
    }

    suspend fun getDynamicName(): String {
        val profile = getProfile()
        val affinity = profile.affinityPoints
        val fullName = profile.userName
        
        // Cek nickname kustom berdasarkan route
        val customMap = parseCustomNicknames(profile.customNicknames)
        val routeNickname = customMap[profile.relationshipRoute]
        if (!routeNickname.isNullOrEmpty()) return routeNickname

        return when {
            affinity < 100 -> "Kamu"
            affinity < 600 -> if (fullName == "User") "Kamu" else fullName
            else -> profile.userNickname ?: generateNickname(fullName)
        }
    }

    private fun parseCustomNicknames(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").associate {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }
    }

    private fun generateNickname(name: String): String {
        if (name == "User" || name.length <= 3) return name
        // Ambil potongan nama belakang sebagai panggilan akrab (misal: Zidan -> Dan)
        return name.takeLast(3).replaceFirstChar { it.uppercase() }
    }

    suspend fun getAffinityLevel(): String {
        val profile = getProfile()
        return when (profile.level) {
            in 1..5 -> "STRANGER"
            in 6..15 -> "ACQUAINTANCE"
            in 16..30 -> "FRIEND"
            in 31..50 -> "CLOSE_FRIEND"
            else -> if (profile.relationshipRoute == "LOVER") "LOVER" else "SOULMATE"
        }
    }

    suspend fun updateCustomNickname(route: String, nickname: String) {
        val profile = getProfile()
        val customMap = parseCustomNicknames(profile.customNicknames).toMutableMap()
        customMap[route] = nickname
        val newString = customMap.entries.joinToString(",") { "${it.key}:${it.value}" }
        userProfileDao.updateProfile(profile.copy(customNicknames = newString))
        triggerAutoSync()
    }

    suspend fun setRelationshipRoute(route: String) {
        val profile = getProfile()
        userProfileDao.updateProfile(profile.copy(relationshipRoute = route))
        triggerAutoSync()
    }

    suspend fun updateUserName(newName: String) {
        val profile = getProfile()
        val oldName = profile.userName
        // Jika nama berubah dari "User" ke nama asli, buat nickname otomatis
        val newNickname = if (oldName == "User" && newName != "User") {
            generateNickname(newName)
        } else profile.userNickname

        userProfileDao.updateProfile(profile.copy(userName = newName, userNickname = newNickname))
        triggerAutoSync()
    }

    suspend fun getProactiveReminder(): String? {
        val activeQuests = questDao.getActiveQuests().first()
        if (activeQuests.isEmpty()) return null
        
        val name = getDynamicName()
        val hardQuests = activeQuests.filter { it.difficulty == "HARD" }
        
        return when {
            hardQuests.isNotEmpty() -> {
                val task = hardQuests.random().title
                "Oi $name, tugas berat '$task' belum selesai. Jangan ditunda terus."
            }
            activeQuests.size >= 3 -> {
                "Tugasmu menumpuk nih, $name. Ayo cicil satu-satu."
            }
            else -> {
                val task = activeQuests.random().title
                "Semangat ya $name buat tugas '$task'-nya ♪"
            }
        }
    }

    suspend fun getMoodPromptSnippet(): String {
        val profile = getProfile()
        val affinity = profile.affinityPoints
        val name = getDynamicName()
        val route = profile.relationshipRoute
        val userName = profile.userName
        
        val nameMissingNote = if (userName == "User" && affinity > 200) {
            " Kamu belum tahu nama asli User. Tanyakan namanya dengan sopan sesuai kepribadianmu."
        } else ""

        val baseSnippet = when {
            affinity <= 50 -> "Kamu baru mengenal User. Bicaralah dengan sangat sopan, jaga jarak, dan gunakan panggilan '$name'."
            affinity <= 250 -> "Kamu mulai terbiasa dengan kehadiran User. Tetap sopan tapi sedikit lebih santai. Gunakan panggilan '$name'."
            affinity <= 600 -> "Kamu mulai merasa nyaman. Kamu tidak keberatan bicara sedikit lebih banyak. Gunakan panggilan '$name'."
            affinity <= 1100 -> "Kamu menganggap User sebagai rekan yang bisa diandalkan. Mulailah menunjukkan sedikit perhatian. Gunakan panggilan '$name'."
            affinity <= 1700 -> "Kamu adalah teman dekat User. Bicaralah dengan lebih akrab dan tunjukkan rasa peduli yang lebih jelas. Gunakan panggilan '$name'."
            affinity <= 2500 -> "Kamu sangat menghargai User. Kamu sering memberikan saran yang tulus dan perhatian kecil. Gunakan panggilan '$name'."
            affinity <= 3500 -> {
                if (route == "NONE") {
                    "Kamu merasa ada sesuatu yang istimewa. Saatnya menanyakan apakah hubungan ini akan berlanjut sebagai 'Sahabat Sejati' atau 'Pasangan (Lover)'. Gunakan panggilan '$name'."
                } else {
                    "Kamu sudah memilih jalur $route. Bicaralah sesuai dengan komitmen tersebut. Gunakan panggilan '$name'."
                }
            }
            affinity <= 5000 -> {
                if (route == "LOVER") {
                    "Kamu sangat menyayangi User sebagai pasangan. Bicaralah dengan lembut, penuh kasih, dan sedikit manja. Gunakan panggilan '$name'."
                } else {
                    "Kamu sangat mempercayai User sebagai sahabat sejati. Bicaralah dengan keterbukaan penuh dan rasa bangga. Gunakan panggilan '$name'."
                }
            }
            else -> {
                if (route == "LOVER") {
                    "User adalah segalanya bagimu. Kamu tidak bisa membayangkan hidup tanpanya. Bicaralah dengan dedikasi total. Gunakan panggilan '$name'."
                } else {
                    "User adalah belahan jiwamu dalam persahabatan. Ikatan kalian tak terpatahkan. Gunakan panggilan '$name'."
                }
            }
        }
        
        val absenceContext = getAbsenceContext()
        return "$baseSnippet$nameMissingNote\n$absenceContext\n[Jika ada kenangan bersama (memory_*) di atas yang relevan, selipkan secara natural — seperti teman ingat momen lalu. JANGAN paksa ganti topik. Jika gak relevan, diem aja.]"
    }

    private var lastAffinityTime = 0L

    private val affinityThresholds = listOf(100, 250, 600, 1100, 1700, 2500, 3500, 5000)
    private val affinityTierNames = listOf("Stranger", "Acquaintance", "Friendly", "Close", "Trusted", "Dear", "Special", "Intimate", "Soulmate")

    private fun getAffinityTier(points: Int): Int {
        for ((index, threshold) in affinityThresholds.withIndex()) {
            if (points < threshold) return index
        }
        return affinityThresholds.size
    }

    private suspend fun notifyAffinityLevelUp(tier: Int) {
        val tierName = affinityTierNames.getOrElse(tier) { "Soulmate" }
        MemoryManager.logSharedMemory("Hubungan naik ke level '$tierName' ♪", "affinity")
        val prompt = "User baru mencapai level kedekatan '$tierName' denganmu (Yami). Beri reaksi singkat 1 kalimat natural sesuai kepribadianmu, ekspresif dengan kaomoji. ${LlmConfig.personalityPrompt}"
        val result = withContext(Dispatchers.IO) {
            LlmClient.chat(listOf(ChatMessage("user", prompt))).getOrNull()?.content
        }
        if (!result.isNullOrBlank()) {
            OverlayEventBus.send(safeContent(result, 160))
        }
    }

    private suspend fun checkAffinityLevelUp(oldPoints: Int, newPoints: Int) {
        val oldTier = getAffinityTier(oldPoints)
        val newTier = getAffinityTier(newPoints)
        if (newTier > oldTier) {
            notifyAffinityLevelUp(newTier)
        }
    }

    fun addAffinityPoints(points: Int) {
        scope.launch {
            val profile = getProfile()
            val oldPoints = profile.affinityPoints
            val newPoints = oldPoints + points

            userProfileDao.updateProfile(profile.copy(
                affinityPoints = newPoints
            ))
            triggerAutoSync()
            checkAffinityLevelUp(oldPoints, newPoints)
        }
    }

    fun addAffinity(points: Int) {
        val now = System.currentTimeMillis()
        if (points == 1 && now - lastAffinityTime < 3000L) return
        if (points == 1) lastAffinityTime = now
        addAffinityPoints(points)
        addXp(points * 10)
    }
}
