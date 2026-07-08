package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.silica.assistant.core.action.Action
import com.silica.assistant.core.action.ActionExecutor
import com.silica.assistant.core.action.ActionMapper
import com.silica.assistant.core.config.AssistantConfig
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.core.system.SoundManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object CommandManager : KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val moodManager: MoodManager by inject()

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
        val lowerInput = rawInput.lowercase().trim()

        // Affinity Logic (Dynamic XP)
        if (lowerInput.contains("terima kasih") || lowerInput.contains("makasih") || lowerInput.contains("thank")) {
            moodManager.addAffinity(5)
            moodManager.updateMood(0.05f)
            SoundManager.playChime()
        } else if (lowerInput.contains("bodoh") || lowerInput.contains("jelek") || lowerInput.contains("benci")) {
            moodManager.addAffinity(-10)
            moodManager.updateMood(-0.1f)
        }

        // Gifting Logic (with Daily Limits & Recovery)
        if (lowerInput.startsWith("kasih hadiah ") || lowerInput.startsWith("give gift ")) {
            val item = lowerInput.removePrefix("kasih hadiah ").removePrefix("give gift ").trim()
            if (item.isNotEmpty()) {
                scope.launch {
                    val (success, response) = moodManager.giveGift(item)
                    OverlayEventBus.onBubble?.invoke(response)
                }
                return
            }
        }

        // --- QUEST SYSTEM COMMANDS ---
        if (lowerInput.startsWith("tambah quest ") || lowerInput.startsWith("add quest ")) {
            val task = lowerInput.removePrefix("tambah quest ").removePrefix("add quest ").trim()
            if (task.isNotEmpty()) {
                val explicitDifficulty = when {
                    task.contains("(hard)") || task.contains("[hard]") -> "HARD"
                    task.contains("(medium)") || task.contains("[medium]") -> "MEDIUM"
                    task.contains("(easy)") || task.contains("[easy]") -> "EASY"
                    else -> null
                }
                val cleanTask = task.replace(Regex("[\\[\\(](hard|easy|medium)[\\]\\)]"), "").trim()
                
                scope.launch {
                    val finalDiff = explicitDifficulty ?: LlmClient.classifyQuestDifficulty(cleanTask) ?: "MEDIUM"
                    moodManager.addQuest(cleanTask, finalDiff)
                    OverlayEventBus.onBubble?.invoke("Oke, aku sudah catat tugas: '$cleanTask' [$finalDiff]. Semangat kerjanya ya")
                }
                return
            }
        }

        if (lowerInput.startsWith("selesai quest ") || lowerInput.startsWith("done quest ")) {
            val task = lowerInput.removePrefix("selesai quest ").removePrefix("done quest ").trim()
            if (task.isNotEmpty()) {
                scope.launch {
                    val result = moodManager.completeQuest(task)
                    SoundManager.playQuestComplete()
                    OverlayEventBus.onBubble?.invoke(result)
                }
                return
            }
        }

        // Music/Genre Search Logic
        if (lowerInput.contains("putar lagu") || lowerInput.contains("play music") || lowerInput.contains("putar genre")) {
            val query = lowerInput.replace("putar lagu", "")
                                  .replace("play music", "")
                                  .replace("putar genre", "")
                                  .trim()
            if (query.isNotEmpty()) {
                MediaController.playFromSearch(context, query)
                return
            }
        }

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
                OverlayEventBus.onBubble?.invoke("Pencarian dinonaktifkan saat mode game")
                return
            }
            OverlayEventBus.onBubble?.invoke("Searching $searchQuery")

            IntentController.searchGoogle(context, searchQuery)

            return
        }

        // dynamic app launcher: "buka discord", "buka whatsapp", etc.
        val normalized = effectiveInput.lowercase().trim()
        if ((normalized.startsWith("buka ") || normalized.startsWith("open ")) &&
                !normalized.matches(
                        Regex("(buka|open) (aplikasi|app|spotify|youtube|browser|pengaturan|settings)")
                )
        ) {

            val prefix = if (normalized.startsWith("buka ")) "buka " else "open "
            val appName = normalized.removePrefix(prefix).trim()

            if (AppLauncher.open(context, appName)) {
                OverlayEventBus.onBubble?.invoke("Membuka $appName")
            } else {
                OverlayEventBus.onBubble?.invoke("Searching $appName")
                IntentController.searchGoogle(context, appName)
            }

            return
        }

        val result = CommandNormalizer.normalize(effectiveInput)

        if (result == null) {
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
                    val custom = AssistantConfig.customGreeting.trim()
                    if (custom.isNotEmpty()) {
                        OverlayEventBus.onBubble?.invoke(custom)
                        return
                    }

                    val personality = LlmConfig.personalityPrompt.lowercase()
                    val reply = when {
                        personality.contains("dingin") || personality.contains("cool") || 
                        personality.contains("tsundere") || personality.contains("cuek") -> {
                            listOf("Hmph, berisik.", "Ada apa?", "Cepat katakan.", "Kenapa panggil-panggil?", "Apa?", "Jangan ganggu.").random()
                        }
                        personality.contains("ceria") || personality.contains("semangat") || 
                        personality.contains("ramah") || personality.contains("lucu") -> {
                            listOf("Iyaaa? Tuan panggil aku?", "Hadir! Ada yang bisa dibantu?", "Halo! Hehe, kangen ya?", "Tuan butuh sesuatu?", "Iya sayang? Eh.. maksudku iya?").random()
                        }
                        personality.contains("sopan") || personality.contains("formal") || 
                        personality.contains("pelayan") || personality.contains("maid") -> {
                            listOf("Saya mendengarkan, Tuan.", "Iya, ada yang bisa saya bantu?", "Menunggu perintah Anda.", "Saya di sini, Tuan.").random()
                        }
                        else -> "Iya? Ada apa?"
                    }
                    OverlayEventBus.onBubble?.invoke(reply)
                } else {
                    scope.launch {
                        val reply = LlmClient.generateScreenComment("Chat", "User bilang: \"$query\". Beri respon SANGAT PENDEK MAKSIMAL 1 KALIMAT. ${LlmConfig.personalityPrompt} Langsung respon.")
                        OverlayEventBus.onBubble?.invoke(reply ?: "...")
                    }
                }
                return
            }

            if (!AppLauncher.open(context, effectiveInput)) {
                OverlayEventBus.onBubble?.invoke("Maaf, saya tidak mengerti \"$effectiveInput\". Coba buka Panduan untuk lihat command yang tersedia.")
            }
            return
        }

        // SSH guard: when terminal visible, block non-AI/SSH commands
        if (OverlayEventBus.isSshActive && result.command !in listOf(
                "ai_task", "ai_task_typing",
                "ssh_status", "ssh_connect", "ssh_disconnect",
                "laptop_info", "chat"
            )) {
            OverlayEventBus.send("Sedang di terminal, command diabaikan")
            return
        }

        // Delegate to Action pipeline
        val action = ActionMapper.map(result)
        ActionExecutor.execute(context, action)
    }
}
