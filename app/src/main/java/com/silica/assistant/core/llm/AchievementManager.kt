package com.silica.assistant.core.llm

import com.silica.assistant.core.llm.db.AchievementDao
import com.silica.assistant.core.llm.model.AchievementEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.overlay.OverlayEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AchievementManager(private val achievementDao: AchievementDao) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val categories = listOf(
        CategoryDef("PEKERJA_KERAS", "Pekerja Keras", "Selesaikan total quest", listOf(1, 5, 10, 25, 50, 100, 250, 500, 750, 1000)),
        CategoryDef("PEJUANG_TANGGUH", "Pejuang Tangguh", "Selesaikan quest HARD", listOf(1, 3, 5, 10, 20, 50, 100, 150, 200, 300)),
        CategoryDef("HARMONI", "Harmoni", "Poin Afinitas", listOf(10, 50, 100, 250, 500, 750, 1000, 1500, 2000, 5000)),
        CategoryDef("DISIPLIN", "Disiplin", "Streak saat ini", listOf(2, 3, 7, 14, 30, 60, 90, 180, 270, 365)),
        CategoryDef("LEGENDA", "Legenda", "Level User", listOf(5, 10, 20, 30, 40, 50, 60, 75, 90, 100)),
        CategoryDef("DERMAWAN", "Dermawan", "Total Hadiah", listOf(1, 5, 10, 20, 40, 60, 80, 100, 150, 200)),
        CategoryDef("KOLEKTOR", "Kolektor", "Jenis item unik", listOf(1, 3, 5, 7, 10, 12, 15, 18, 20, 25)),
        CategoryDef("INTERAKTIF", "Interaktif", "Perintah Suara", listOf(10, 50, 100, 250, 500, 1000, 2000, 3000, 4000, 5000)),
        CategoryDef("TEMAN_SETIA", "Teman Setia", "Hari sejak install", listOf(1, 7, 14, 30, 60, 90, 180, 365, 500, 1000)),
        CategoryDef("PUNCAK_MOOD", "Puncak Mood", "Mood maks (hari)", listOf(1, 3, 5, 7, 10, 14, 20, 30, 45, 60))
    )

    data class CategoryDef(val id: String, val name: String, val descPrefix: String, val targets: List<Int>)

    fun initAchievements() {
        scope.launch {
            if (achievementDao.getCount() == 0) {
                val list = mutableListOf<AchievementEntity>()
                categories.forEach { cat ->
                    cat.targets.forEachIndexed { index, target ->
                        list.add(AchievementEntity(
                            id = "${cat.id}_${index + 1}",
                            category = cat.name,
                            title = "${cat.name} ${index + 1}",
                            description = "${cat.descPrefix} sebanyak $target",
                            tier = index + 1,
                            targetValue = target
                        ))
                    }
                }
                achievementDao.insertAchievements(list)
            }
        }
    }

    suspend fun checkAchievements(profile: UserProfileEntity, totalQuests: Int, hardQuests: Int) {
        val achievements = mutableListOf<AchievementEntity>()
        // We'll fetch them all and update in memory, then save. 
        // For 100 items, this is fine in Room.
        
        val currentData = mutableMapOf<String, Int>()
        currentData["PEKERJA_KERAS"] = totalQuests
        currentData["PEJUANG_TANGGUH"] = hardQuests
        currentData["HARMONI"] = profile.affinityPoints
        currentData["DISIPLIN"] = profile.currentStreak
        currentData["LEGENDA"] = profile.level
        currentData["DERMAWAN"] = profile.dailyGiftCount // This needs to be persistent total gifts, but for now we'll use profile field or similar
        // Note: For Sultan/Dermawan, we might need a totalGiftCount in Profile. 
        // I'll use profile.dailyGiftCount for now as placeholder, but it resets daily.
        currentData["KOLEKTOR"] = if (profile.inventory.isBlank()) 0 else profile.inventory.split(",").distinct().size
        currentData["INTERAKTIF"] = 0 // Placeholder for voice command count
        currentData["TEMAN_SETIA"] = 1 // Placeholder
        currentData["PUNCAK_MOOD"] = if (profile.mood >= 1.49f) 1 else 0 // Placeholder

        categories.forEach { cat ->
            val value = currentData[cat.id] ?: 0
            for (tier in 1..10) {
                val id = "${cat.id}_$tier"
                val ach = achievementDao.getAchievementById(id)
                if (ach != null && !ach.isUnlocked && value >= ach.targetValue) {
                    val updated = ach.copy(
                        currentValue = value,
                        isUnlocked = true,
                        unlockedAt = System.currentTimeMillis()
                    )
                    achievementDao.updateAchievement(updated)
                    notifyAchievement(updated)
                } else if (ach != null && !ach.isUnlocked && value != ach.currentValue) {
                    achievementDao.updateAchievement(ach.copy(currentValue = value))
                }
            }
        }
    }

    private fun notifyAchievement(achievement: AchievementEntity) {
        scope.launch(Dispatchers.Main) {
            OverlayEventBus.onBubble?.invoke("🎊 **ACHIEVEMENT UNLOCKED!** 🎊 (＾▽＾)\n'${achievement.title}'\n${achievement.description}")
        }
    }
}

data class CategoryDef(val id: String, val name: String, val descPrefix: String, val targets: List<Int>)
