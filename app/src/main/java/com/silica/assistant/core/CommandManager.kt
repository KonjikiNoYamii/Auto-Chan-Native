package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.silica.assistant.core.config.AssistantConfig
import com.silica.assistant.core.knowledge.KnowledgeEngine
import com.silica.assistant.core.knowledge.KnowledgeParser
import com.silica.assistant.core.media.MediaController
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.parser.SearchCommandParser
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.core.system.AppLauncher
import com.silica.assistant.core.system.BrightnessController
import com.silica.assistant.service.OverlayService

object CommandManager {

    fun execute(context: Context, rawInput: String) {
        // direct command keys (from button clicks) bypass wake word filter
        val commandKeys = CommandAliases.aliases.keys
        
        val effectiveInput =
                if (rawInput.trim().lowercase() in commandKeys) {

                    rawInput.lowercase().trim()
                } else {

                    if (AssistantConfig.requireWakeWord) {

                        val wake = WakeWord.extractCommand(rawInput) ?: return

                        wake
                    } else {

                        rawInput.lowercase().trim()
                    }
                }

        CommandHistoryManager.add(effectiveInput)

        val searchQuery = SearchCommandParser.parse(effectiveInput)

        if (searchQuery != null) {

            OverlayEventBus.onBubble?.invoke("🔎 Searching $searchQuery")

            IntentController.searchGoogle(context, searchQuery)

            return
        }

        // dynamic app launcher: "buka discord", "buka whatsapp", "open whatsapp", etc.
        val normalized = effectiveInput.lowercase().trim()
        if ((normalized.startsWith("buka ") || normalized.startsWith("open ")) &&
                !normalized.matches(
                        Regex("(buka|open) (aplikasi|app|spotify|youtube|browser|pengaturan|settings)")
                )
        ) {

            val prefix = if (normalized.startsWith("buka ")) "buka " else "open "
            val appName = normalized.removePrefix(prefix).trim()

            if (AppLauncher.open(context, appName)) {
                OverlayEventBus.onBubble?.invoke("📱 Membuka $appName")
            } else {
                OverlayEventBus.onBubble?.invoke("🔎 Searching $appName")
                IntentController.searchGoogle(context, appName)
            }

            return
        }

        val result = CommandNormalizer.normalize(effectiveInput)
        val knowledgeQuery = KnowledgeParser.parse(effectiveInput)

        if (knowledgeQuery != null) {

            val answer = KnowledgeEngine.answer(knowledgeQuery)

            OverlayEventBus.send(answer)

            return
        }

        if (result == null) {
            if (!AppLauncher.open(context, effectiveInput)) {
                OverlayEventBus.onBubble?.invoke("😕 Maaf, saya tidak mengerti \"$effectiveInput\". Coba buka Panduan untuk lihat command yang tersedia.")
            }
            return
        }
        when (result.command) {
            "open_spotify" -> {
                IntentController.openSpotify(context)
            }
            "open_youtube" -> {
                IntentController.openYoutube(context)
            }
            "open_browser" -> {
                IntentController.openBrowser(context)
            }
            "open_settings" -> {
                IntentController.openSettings(context)
            }
            "start_overlay" -> {

                if (Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, OverlayService::class.java)
                    context.startService(intent)
                    OverlayEventBus.onBubble?.invoke("🌸 Waifu activated!")
                } else {
                    val intent =
                            Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                            )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }

            "stop_overlay" -> {
                val intent = Intent(context, OverlayService::class.java)
                context.stopService(intent)
                Toast.makeText(context, "Overlay closed", Toast.LENGTH_SHORT).show()
            }
            "media_play_pause" -> {
                MediaController.playPause(context)
                OverlayEventBus.onBubble?.invoke("🎵 Toggle Music")
            }
            "media_next" -> {
                MediaController.next(context)
                OverlayEventBus.onBubble?.invoke("⏭ Next Song")
            }
            "media_previous" -> {
                MediaController.previous(context)
                OverlayEventBus.onBubble?.invoke("⏮ Previous Song")
            }
            "open_app" -> {
                OverlayEventBus.onBubble?.invoke("Aplikasi apa yang ingin dibuka?")
            }
            "volume_up" -> {
                com.silica.assistant.core.system.VolumeController.volumeUp(context)
                OverlayEventBus.onBubble?.invoke("🔊 Volume Naik")
            }
            "volume_down" -> {
                com.silica.assistant.core.system.VolumeController.volumeDown(context)
                OverlayEventBus.onBubble?.invoke("🔉 Volume Turun")
            }
            "mute_volume" -> {
                com.silica.assistant.core.system.VolumeController.mute(context)
                OverlayEventBus.onBubble?.invoke("🔇 Mute")
            }
            "max_volume" -> {
                com.silica.assistant.core.system.VolumeController.maxVolume(context)
                OverlayEventBus.onBubble?.invoke("📢 Volume Maksimal")
            }
            "brightness_up" -> {
                BrightnessController.increase(context)
                OverlayEventBus.onBubble?.invoke("☀️ Brightness Naik")
            }
            "brightness_down" -> {
                BrightnessController.decrease(context)
                OverlayEventBus.onBubble?.invoke("🌙 Brightness Turun")
            }
            "brightness_max" -> {
                BrightnessController.max(context)
                OverlayEventBus.onBubble?.invoke("🔆 Brightness Maksimal")
            }
            "brightness_min" -> {
                BrightnessController.min(context)
                OverlayEventBus.onBubble?.invoke("🌑 Brightness Minimum")
            }
            "ssh_status" -> {
                val connected = SshManager.isConnected()
                val msg = if (connected) {
                    val conn = SshManager.getCurrentConnection()
                    "✅ SSH terhubung ke ${conn?.name ?: "laptop"}"
                } else {
                    "❌ SSH tidak terhubung"
                }
                OverlayEventBus.onBubble?.invoke(msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            "ssh_connect" -> {
                OverlayEventBus.navigateScreen.value = "ssh"
                OverlayEventBus.onBubble?.invoke("🔌 Membuka SSH...")
            }
            "ssh_disconnect" -> {
                SshManager.disconnect()
                OverlayEventBus.onBubble?.invoke("🔌 SSH terputus")
                Toast.makeText(context, "SSH disconnected", Toast.LENGTH_SHORT).show()
            }
            "chat" -> {
                OverlayEventBus.navigateScreen.value = "chat"
                OverlayEventBus.onBubble?.invoke("💬 Membuka Chat AI...")
            }
            "laptop_info" -> {
                if (!SshManager.isConnected()) {
                    OverlayEventBus.onBubble?.invoke("❌ SSH tidak terhubung")
                    return
                }
                Thread {
                    SshManager.executeCommand("uptime && echo '---' && free -h | head -3 && echo '---' && df -h / | tail -1")
                        .onSuccess { result ->
                            val lines = result.lines().take(8)
                            OverlayEventBus.onBubble?.invoke("📊 " + lines.joinToString(" | "))
                        }
                        .onFailure { e ->
                            OverlayEventBus.onBubble?.invoke("❌ Gagal: ${e.message}")
                        }
                }.start()
            }
            else -> {
                OverlayEventBus.onBubble?.invoke("😕 Maaf, saya tidak mengerti command \"${result.command}\"")
            }
        }
    }
}
