package com.silica.assistant.core.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.silica.assistant.core.IntentController
import com.silica.assistant.core.CommandAliases
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.media.MediaController
import com.silica.assistant.core.system.BrightnessController
import com.silica.assistant.core.system.VolumeController
import com.silica.assistant.core.system.AppLauncher
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.overlay.GameModeManager
import com.silica.assistant.service.OverlayService
import com.silica.assistant.core.llm.MoodManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object ActionExecutor : KoinComponent {
    private val moodManager: MoodManager by inject()

    fun execute(context: Context, action: Action) {
        when (action) {
            // ── App Launchers ──
            is Action.OpenSpotify -> IntentController.openSpotify(context)
            is Action.OpenYoutube -> IntentController.openYoutube(context)
            is Action.OpenBrowser -> IntentController.openBrowser(context)
            is Action.OpenSettings -> IntentController.openSettings(context)
            is Action.OpenApp -> {
                OverlayEventBus.onBubble?.invoke("Aplikasi apa yang ingin dibuka?")
            }

            // ── Overlay ──
            is Action.StartOverlay -> {
                if (Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, OverlayService::class.java)
                    context.startService(intent)
                    OverlayEventBus.onBubble?.invoke("Waifu activated! (^-^)")
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            is Action.StopOverlay -> {
                val intent = Intent(context, OverlayService::class.java)
                context.stopService(intent)
                Toast.makeText(context, "Overlay closed", Toast.LENGTH_SHORT).show()
            }

            // ── Volume ──
            is Action.VolumeUp -> {
                VolumeController.volumeUp(context)
                OverlayEventBus.onBubble?.invoke("Volume Naik")
            }
            is Action.VolumeDown -> {
                VolumeController.volumeDown(context)
                OverlayEventBus.onBubble?.invoke("Volume Turun")
            }
            is Action.MuteVolume -> {
                VolumeController.mute(context)
                OverlayEventBus.onBubble?.invoke("Mute")
            }
            is Action.MaxVolume -> {
                VolumeController.maxVolume(context)
                OverlayEventBus.onBubble?.invoke("Volume Maksimal")
            }

            // ── Brightness ──
            is Action.BrightnessUp -> {
                BrightnessController.increase(context)
                OverlayEventBus.onBubble?.invoke("Brightness Naik")
            }
            is Action.BrightnessDown -> {
                BrightnessController.decrease(context)
                OverlayEventBus.onBubble?.invoke("Brightness Turun")
            }
            is Action.BrightnessMax -> {
                BrightnessController.max(context)
                OverlayEventBus.onBubble?.invoke("Brightness Maksimal")
            }
            is Action.BrightnessMin -> {
                BrightnessController.min(context)
                OverlayEventBus.onBubble?.invoke("Brightness Minimum")
            }

            // ── Media ──
            is Action.MediaPlayPause -> {
                MediaController.playPause(context)
                OverlayEventBus.onBubble?.invoke("Toggle Music")
            }
            is Action.MediaNext -> {
                MediaController.next(context)
                OverlayEventBus.onBubble?.invoke("Next Song")
            }
            is Action.MediaPrevious -> {
                MediaController.previous(context)
                OverlayEventBus.onBubble?.invoke("Previous Song")
            }

            // ── SSH ──
            is Action.SshStatus -> {
                val connected = SshManager.isConnected()
                val msg = if (connected) {
                    val conn = SshManager.getCurrentConnection()
                    "SSH terhubung ke ${conn?.name ?: "laptop"}"
                } else {
                    "SSH tidak terhubung"
                }
                OverlayEventBus.onBubble?.invoke(msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            is Action.SshConnect -> {
                OverlayEventBus.navigateScreen.value = "ssh"
                OverlayEventBus.onBubble?.invoke("Membuka SSH...")
            }
            is Action.SshDisconnect -> {
                SshManager.disconnect()
                OverlayEventBus.onBubble?.invoke("SSH terputus")
                Toast.makeText(context, "SSH disconnected", Toast.LENGTH_SHORT).show()
            }
            is Action.LaptopInfo -> {
                if (!SshManager.isConnected()) {
                    OverlayEventBus.onBubble?.invoke("SSH tidak terhubung")
                    return
                }
                Thread {
                    SshManager.executeCommand("uptime && echo '---' && free -h | head -3 && echo '---' && df -h / | tail -1")
                        .onSuccess { result ->
                            val lines = result.lines().take(8)
                            OverlayEventBus.onBubble?.invoke(lines.joinToString(" | "))
                        }
                        .onFailure { e ->
                            OverlayEventBus.onBubble?.invoke("Gagal: ${e.message}")
                        }
                }.start()
            }

            // ── AI ──
            is Action.Chat -> {
                OverlayEventBus.navigateScreen.value = "chat"
                OverlayEventBus.onBubble?.invoke("Membuka Chat AI...")
            }
            is Action.AiTask -> {
                moodManager.consumeStamina(0.2f)
                OverlayEventBus.aiTerminalPrompt = action.context.ifBlank { action.rawInput }
                OverlayEventBus.send("Sedang merencanakan...")
                OverlayEventBus.navigateScreen.value = "ssh"
            }
            is Action.AiTaskTyping -> {
                OverlayEventBus.aiTerminalPrompt = ""
                OverlayEventBus.navigateScreen.value = "ssh"
            }

            // ── Debug ──
            is Action.OpenDebug -> {
                OverlayEventBus.navigateScreen.value = "debug"
                OverlayEventBus.onBubble?.invoke("Membuka debug AI...")
            }

            // ── Game Mode ──
            is Action.GameMode -> {
                OverlayEventBus.gameModeRequest = true
                OverlayEventBus.onBubble?.invoke("Mode game diaktifkan")
            }
            is Action.StopGameMode -> {
                OverlayEventBus.gameModeRequest = false
                OverlayEventBus.onBubble?.invoke("Mode game dinonaktifkan")
            }
            is Action.SetGameModeApp -> {
                val pkg = GameModeManager.currentAppPackage
                if (pkg != null) {
                    GameModeManager.gameModeAppPackage = pkg
                    val name = GameModeManager.currentAppName ?: pkg
                    OverlayEventBus.onBubble?.invoke("Game mode terdeteksi dari $name")
                } else {
                    OverlayEventBus.onBubble?.invoke("Tidak ada aplikasi terdeteksi. Coba buka game mode dulu.")
                }
            }
            is Action.ClearGameModeApp -> {
                GameModeManager.gameModeAppPackage = null
                OverlayEventBus.onBubble?.invoke("Game mode app direset")
            }

            // ── Screen ──
            is Action.ScreenInfo -> {
                moodManager.consumeStamina(0.1f)
                OverlayEventBus.onBubble?.invoke("...")
                OverlayEventBus.screenCaptureCallback?.invoke()
            }
            is Action.GameComment -> {
                moodManager.consumeStamina(0.05f)
                OverlayEventBus.gameCommentCallback?.invoke(action.context)
            }

            // ── Accessibility ──
            is Action.ClickElement -> {
                val acc = OverlayEventBus.accessibilityService
                val keyword = action.keyword
                if (keyword.isNotBlank()) {
                    val tried = mutableSetOf<String>()
                    val variants = keywordVariants(keyword)
                    var clicked = false
                    for (v in variants) {
                        if (!tried.add(v)) continue
                        if (acc?.findAndClick(v) == true) {
                            clicked = true
                            break
                        }
                    }
                    if (clicked) {
                        OverlayEventBus.onBubble?.invoke("Udah diklik~")
                    } else {
                        var launched = false
                        for (v in variants) {
                            if (AppLauncher.open(context, v)) {
                                OverlayEventBus.onBubble?.invoke("Membuka $v")
                                launched = true
                                break
                            }
                        }
                        if (!launched) {
                            OverlayEventBus.onBubble?.invoke("Nggak nemu \"$keyword\"")
                        }
                    }
                }
            }
            is Action.ScrollDown -> {
                if (OverlayEventBus.accessibilityService?.performScrollDown() == true) {
                    OverlayEventBus.onBubble?.invoke("Scroll ke bawah")
                } else {
                    OverlayEventBus.onBubble?.invoke("Gagal scroll")
                }
            }
            is Action.ScrollUp -> {
                if (OverlayEventBus.accessibilityService?.performScrollUp() == true) {
                    OverlayEventBus.onBubble?.invoke("Scroll ke atas")
                } else {
                    OverlayEventBus.onBubble?.invoke("Gagal scroll")
                }
            }
            is Action.GoBack -> {
                if (OverlayEventBus.accessibilityService?.performGlobalBack() == true) {
                    OverlayEventBus.onBubble?.invoke("Kembali")
                } else {
                    OverlayEventBus.onBubble?.invoke("Gagal kembali")
                }
            }

            // ── Unknown ──
            is Action.Unknown -> {
                Toast.makeText(context, "Unknown action: ${action.raw}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun keywordVariants(keyword: String): List<String> {
        val base = keyword.lowercase().trim()
        val variants = mutableListOf<String>()
        variants.add(base)
        val withCK = base.replace('c', 'k')
        if (withCK != base) variants.add(withCK)
        val withC = base.replace('k', 'c')
        if (withC != base && withC !in variants) variants.add(withC)
        val withIE = base.replace("ie", "i")
        if (withIE != base && withIE !in variants) variants.add(withIE)
        val withIe = base.replace("i", "ie")
        if (withIe != base && withIe !in variants) variants.add(withIe)
        return variants
    }
}
