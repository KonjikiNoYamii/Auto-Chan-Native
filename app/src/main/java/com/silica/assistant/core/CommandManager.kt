package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.silica.assistant.core.knowledge.KnowledgeEngine
import com.silica.assistant.core.knowledge.KnowledgeParser
import com.silica.assistant.core.media.MediaController
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.parser.SearchCommandParser
import com.silica.assistant.service.OverlayService

object CommandManager {

    fun execute(context: Context, rawInput: String) {
        CommandHistoryManager.add(rawInput)

        val searchQuery = SearchCommandParser.parse(rawInput)

        if (searchQuery != null) {

            OverlayEventBus.onBubble?.invoke("🔎 Searching $searchQuery")

            IntentController.searchGoogle(context, searchQuery)

            return
        }

        val result = CommandNormalizer.normalize(rawInput)
        val knowledgeQuery = KnowledgeParser.parse(rawInput)

        if (knowledgeQuery != null) {

            val answer = KnowledgeEngine.answer(knowledgeQuery)

            OverlayEventBus.send(answer)

            return
        }

        if (result == null) {
            Toast.makeText(context, "Command not recognized", Toast.LENGTH_SHORT).show()
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
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
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
            else -> {
                Toast.makeText(
                                context,
                                "Command: ${result.command} (${result.confidence})",
                                Toast.LENGTH_SHORT
                        )
                        .show()
            }
        }
    }
}
