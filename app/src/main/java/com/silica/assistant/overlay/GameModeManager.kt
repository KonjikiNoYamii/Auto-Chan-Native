package com.silica.assistant.overlay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.DisplayMetrics

enum class GameModePosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

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
        if (knownGameModeApps.any { packageName.startsWith(it) }) return true
        if (knownGamePackages.any { packageName.startsWith(it) }) return true
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
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
            previousX = x
            previousY = y
            previousState = WaifuStateManager.currentState
        }
        isGameMode = true
        autoGameMode = auto
        WaifuStateManager.currentState = WaifuState.GAME
        return positionForGameMode(context)
    }

    fun exitGameMode(resetManual: Boolean = true): Pair<Int, Int> {
        if (!isGameMode) return 0 to 0
        isGameMode = false
        autoGameMode = false
        if (resetManual) manualMode = false
        WaifuStateManager.currentState = previousState
        return previousX to previousY
    }

    private fun positionForGameMode(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return 0 to 0
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val half = (60 * metrics.density).toInt()

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
