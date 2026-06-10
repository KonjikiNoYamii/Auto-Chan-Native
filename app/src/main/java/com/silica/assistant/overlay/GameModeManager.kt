package com.silica.assistant.overlay

import android.content.Context
import android.content.pm.ApplicationInfo

enum class GameModePosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

enum class GameAccessibilityLevel { UNKNOWN, NONE, PARTIAL, FULL }

object GameModeManager {
    var isGameMode = false
    var gameModePosition = GameModePosition.TOP_CENTER
    var currentAppPackage: String? = null
    var currentAppName: String? = null
    var manualMode = false
    var autoGameMode = true
    var gameModeAppPackage: String? = null

    private val knownGameModeApps = setOf(
        // Tecno / Infinix / Itel GameSpace
        "com.transsion.smartpanel",
        // Xiaomi Game Turbo
        "com.xiaomi.gamecenter.sdk.service",
        "com.xiaomi.glgm",
        // Samsung Game Launcher / Game Tools
        "com.samsung.android.game.gamehome",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gamesdk",
        // OPPO / Realme Game Space
        "com.coloros.gameassistant",
        "com.heytap.gameassistant",
        "com.coloros.gamespace",
        // Vivo Game Mode
        "com.vivo.game",
        "com.bbk.launcher2",
        // Huawei Game Center
        "com.huawei.gameassistant",
        // ASUS Game Genie
        "com.asus.gamecenter",
        "com.asus.glide",
        // Lenovo Game Mode
        "com.lenovo.game",
        // Meizu Game Mode
        "com.meizu.game",
        // Nothing Phone
        "com.nothing.game",
        // Google Play Games
        "com.google.android.play.games",
    )

    private val knownGamePackages = setOf(
        "com.nexon.bluearchive",
        "com.mobile.legends",
        "com.igg.android.illusionsconnect",
        "com.tencent.ig",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.garena.game.codm",
        "com.tencent.tmgp.sgame",
        "com.miHoYo.GenshinImpact",
        "com.miHoYo.Yuanshen",
        "com.HoYoverse.Nap",
        "com.riotgames.league.wildrift",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.supercell.brawlstars",
        "com.king.candycrushsaga",
        "com.pubg.imobile",
        "com.pubg.newstate",
        "com.activision.callofduty.shooter",
        "com.valvesoftware.android.steam.community",
        "com.ea.gp.fifa",
        "com.ea.game.pvz2_row",
        "com.roblox.client",
        "com.mojang.minecraftpe",
    )

    private var previousX = 0
    private var previousY = 0
    private var previousState = WaifuState.RELAX

    fun isGame(context: Context, packageName: String): Boolean {
        // Fallback checks
        if (knownGameModeApps.any { packageName.startsWith(it) }) return true
        if (knownGamePackages.any { packageName.startsWith(it) }) return true

        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val isGameCategory = info.category == ApplicationInfo.CATEGORY_GAME
            
            // Logging for diagnostic
            android.util.Log.d("GameModeManager", "isGame: pkg=$packageName, category=${info.category}, isGameCategory=$isGameCategory")
            
            isGameCategory
        } catch (e: Exception) {
            android.util.Log.e("GameModeManager", "isGame failed for $packageName", e)
            false
        }
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun enterGameMode(context: Context, x: Int, y: Int, auto: Boolean = false): Pair<Int, Int> {
        if (isGameMode && auto == autoGameMode) return x to y
        if (!isGameMode) {
            if (!auto) {
                previousX = x
                previousY = y
            }
            previousState = WaifuStateManager.currentState
        }
        isGameMode = true
        autoGameMode = auto
        WaifuStateManager.currentState = WaifuState.GAME
        val metrics = context.resources.displayMetrics
        return positionForGameMode(metrics.widthPixels, metrics.heightPixels, metrics.density)
    }

    fun enterGameMode(context: Context, x: Int, y: Int, displayW: Int, displayH: Int, density: Float, auto: Boolean = false): Pair<Int, Int> {
        if (isGameMode && auto == autoGameMode) return x to y
        if (!isGameMode) {
            if (!auto) {
                previousX = x
                previousY = y
            }
            previousState = WaifuStateManager.currentState
        }
        isGameMode = true
        autoGameMode = auto
        WaifuStateManager.currentState = WaifuState.GAME
        return positionForGameMode(displayW, displayH, density)
    }

    fun exitGameMode(resetManual: Boolean = true): Pair<Int, Int> {
        if (!isGameMode) return 0 to 0
        isGameMode = false
        autoGameMode = false
        if (resetManual) manualMode = false
        WaifuStateManager.currentState = previousState
        return previousX to previousY
    }

    // ── Accessibility level tracking per game ──
    fun getAccessibilityLevel(pkg: String): GameAccessibilityLevel =
        accessibilityCache[pkg] ?: GameAccessibilityLevel.UNKNOWN

    fun recordAccessibilitySample(pkg: String, hasText: Boolean) {
        if (pkg.isBlank()) return
        val samples = accessibilitySamples.getOrPut(pkg) { mutableListOf() }
        samples.add(hasText)
        if (samples.size > 10) samples.removeAt(0)
        if (samples.size >= 3) {
            val ratio = samples.count { it }.toFloat() / samples.size
            accessibilityCache[pkg] = when {
                ratio >= 0.6f -> GameAccessibilityLevel.FULL
                ratio >= 0.2f -> GameAccessibilityLevel.PARTIAL
                else -> GameAccessibilityLevel.NONE
            }
        }
    }

    private val accessibilitySamples = mutableMapOf<String, MutableList<Boolean>>()
    private val accessibilityCache = mutableMapOf<String, GameAccessibilityLevel>()

    private fun positionForGameMode(w: Int, h: Int, density: Float): Pair<Int, Int> {
        val half = (60 * density).toInt()
        return when (gameModePosition) {
            GameModePosition.TOP_LEFT -> 0 to 0
            GameModePosition.TOP_CENTER -> (w / 2 - half) to 0
            GameModePosition.TOP_RIGHT -> (w - half * 2) to 0
            GameModePosition.CENTER_LEFT -> 0 to (h / 2 - half)
            GameModePosition.CENTER_RIGHT -> (w - half * 2) to (h / 2 - half)
            GameModePosition.BOTTOM_LEFT -> 0 to (h - half * 2)
            GameModePosition.BOTTOM_CENTER -> (w / 2 - half) to (h - half * 2)
            GameModePosition.BOTTOM_RIGHT -> (w - half * 2) to (h - half * 2)
        }
    }
}
