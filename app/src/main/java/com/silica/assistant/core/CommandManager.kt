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
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.llm.LlmConfig
import com.silica.assistant.core.parser.SearchCommandParser
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.core.system.AppLauncher
import com.silica.assistant.core.system.BrightnessController
import com.silica.assistant.overlay.GameModeManager
import com.silica.assistant.service.OverlayService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CommandManager {

    @OptIn(DelicateCoroutinesApi::class)
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
            if (GameModeManager.isGameMode) {
                OverlayEventBus.onBubble?.invoke("🔇 Pencarian dinonaktifkan saat mode game")
                return
            }
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
            // AI task detection for non-matched commands
            val taskIndicators = listOf("buat", "bikin", "tulis", "kerjakan", "buatin", "bikinin", "tuliskan", "lakukan", "eksekusi", "buatkan")
            val firstWord = normalized.split(" ").firstOrNull() ?: ""
            if (firstWord in taskIndicators || taskIndicators.any { normalized.startsWith("$it ") }) {
                OverlayEventBus.aiTaskCallback?.invoke(effectiveInput)
                return
            }

            // Check if user is calling her name
            val assistantName = AssistantConfig.assistantName.lowercase()
            if (normalized == assistantName || normalized.startsWith("$assistantName ")) {
                if (LlmClient.activeProvider == "Memeriksa...") {
                    val msg = if (LlmConfig.personalityPrompt.lowercase().contains("dingin") || 
                                 LlmConfig.personalityPrompt.lowercase().contains("cool")) {
                        "Bentar, aku siap-siap dulu."
                    } else {
                        "Sebentar ya, aku siap-siap terlebih dahulu."
                    }
                    OverlayEventBus.onBubble?.invoke(msg)
                    return
                }

                val query = if (normalized == assistantName) "" else normalized.removePrefix("$assistantName ").trim()
                if (query.isEmpty()) {
                    // 1. Priority: User-defined custom greeting (Hardcoded)
                    val custom = AssistantConfig.customGreeting.trim()
                    if (custom.isNotEmpty()) {
                        OverlayEventBus.onBubble?.invoke(custom)
                        return
                    }

                    // 2. Fallback: Smart Hardcoded Personality (Hardcoded)
                    val personality = LlmConfig.personalityPrompt.lowercase()
                    val reply = when {
                        // Cool / Tsundere
                        personality.contains("dingin") || personality.contains("cool") || 
                        personality.contains("tsundere") || personality.contains("cuek") -> {
                            listOf("Hmph, berisik.", "Ada apa?", "Cepat katakan.", "Kenapa panggil-panggil?", "Apa?", "Jangan ganggu.").random()
                        }
                        // Cheerful / Cute
                        personality.contains("ceria") || personality.contains("semangat") || 
                        personality.contains("ramah") || personality.contains("lucu") -> {
                            listOf("Iyaaa? Tuan panggil aku?", "Hadir! Ada yang bisa dibantu?", "Halo! Hehe, kangen ya?", "Tuan butuh sesuatu?", "Iya sayang? Eh.. maksudku iya?").random()
                        }
                        // Polite / Formal
                        personality.contains("sopan") || personality.contains("formal") || 
                        personality.contains("pelayan") || personality.contains("maid") -> {
                            listOf("Saya mendengarkan, Tuan.", "Iya, ada yang bisa saya bantu?", "Menunggu perintah Anda.", "Saya di sini, Tuan.").random()
                        }
                        else -> "Iya? Ada apa?"
                    }
                    OverlayEventBus.onBubble?.invoke(reply)
                } else {
                    // Chatting with her (use AI but very short)
                    kotlinx.coroutines.GlobalScope.launch {
                        val reply = LlmClient.generateScreenComment("Chat", "User bilang: \"$query\". Beri respon SANGAT PENDEK MAKSIMAL 1 KALIMAT. ${LlmConfig.personalityPrompt} Langsung respon.")
                        OverlayEventBus.onBubble?.invoke(reply ?: "...")
                    }
                }
                return
            }

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
            "game_mode" -> {
                OverlayEventBus.gameModeRequest = true
                OverlayEventBus.onBubble?.invoke("🎮 Mode game diaktifkan")
            }
            "stop_game_mode" -> {
                OverlayEventBus.gameModeRequest = false
                OverlayEventBus.onBubble?.invoke("🎮 Mode game dinonaktifkan")
            }
            "set_game_mode_app" -> {
                val pkg = GameModeManager.currentAppPackage
                if (pkg != null) {
                    GameModeManager.gameModeAppPackage = pkg
                    val name = GameModeManager.currentAppName ?: pkg
                    OverlayEventBus.onBubble?.invoke("✅ Game mode terdeteksi dari $name")
                } else {
                    OverlayEventBus.onBubble?.invoke("❌ Tidak ada aplikasi terdeteksi. Coba buka game mode dulu.")
                }
            }
            "clear_game_mode_app" -> {
                GameModeManager.gameModeAppPackage = null
                OverlayEventBus.onBubble?.invoke("✅ Game mode app direset")
            }
            "screen_info" -> {
                OverlayEventBus.onBubble?.invoke("🔍...")
                OverlayEventBus.screenCaptureCallback?.invoke()
            }
            "game_comment" -> {
                val contextHint = extractContext(effectiveInput, "game_comment")
                OverlayEventBus.gameCommentCallback?.invoke(contextHint)
            }
            "ai_task" -> {
                val contextHint = extractContext(effectiveInput, "ai_task")
                OverlayEventBus.aiTaskCallback?.invoke(contextHint.ifBlank { effectiveInput })
            }
            "open_debug" -> {
                OverlayEventBus.navigateScreen.value = "debug"
                OverlayEventBus.onBubble?.invoke("📊 Membuka debug AI...")
            }
            "click_element" -> {
                val keyword = effectiveInput
                    .removePrefix("klik ").removePrefix("tekan ").removePrefix("tap ")
                    .trim()
                if (keyword.isNotBlank()) {
                    val tried = mutableSetOf<String>()
                    val variants = keywordVariants(keyword)
                    var clicked = false
                    for (v in variants) {
                        if (!tried.add(v)) continue
                        if (OverlayEventBus.accessibilityService?.findAndClick(v) == true) {
                            clicked = true
                            break
                        }
                    }
                    if (clicked) {
                        OverlayEventBus.onBubble?.invoke("✅ Udah diklik~")
                    } else {
                        var launched = false
                        for (v in variants) {
                            if (AppLauncher.open(context, v)) {
                                OverlayEventBus.onBubble?.invoke("📱 Membuka $v")
                                launched = true
                                break
                            }
                        }
                        if (!launched) {
                            OverlayEventBus.onBubble?.invoke("❌ Nggak nemu \"$keyword\"")
                        }
                    }
                }
            }
            "scroll_down" -> {
                if (OverlayEventBus.accessibilityService?.performScrollDown() == true) {
                    OverlayEventBus.onBubble?.invoke("📜 Scroll ke bawah")
                } else {
                    OverlayEventBus.onBubble?.invoke("❌ Gagal scroll")
                }
            }
            "scroll_up" -> {
                if (OverlayEventBus.accessibilityService?.performScrollUp() == true) {
                    OverlayEventBus.onBubble?.invoke("📜 Scroll ke atas")
                } else {
                    OverlayEventBus.onBubble?.invoke("❌ Gagal scroll")
                }
            }
            "go_back" -> {
                if (OverlayEventBus.accessibilityService?.performGlobalBack() == true) {
                    OverlayEventBus.onBubble?.invoke("⬅ Kembali")
                } else {
                    OverlayEventBus.onBubble?.invoke("❌ Gagal kembali")
                }
            }
            else -> {
                OverlayEventBus.onBubble?.invoke("😕 Maaf, saya tidak mengerti command \"${result.command}\"")
            }
        }
    }

    private fun extractContext(input: String, commandKey: String): String {
        val lower = input.lowercase().trim()
        val aliases = CommandAliases.aliases[commandKey] ?: return ""
        for (alias in aliases.sortedByDescending { it.length }) {
            if (lower.startsWith(alias)) {
                return lower.removePrefix(alias).trim()
            }
        }
        return ""
    }
}
