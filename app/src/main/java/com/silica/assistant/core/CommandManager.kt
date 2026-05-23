package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.silica.assistant.core.knowledge.KnowledgeEngine
import com.silica.assistant.core.knowledge.KnowledgeParser
import com.silica.assistant.core.media.MediaController
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.service.OverlayService

object CommandManager {

    fun execute(context: Context, rawInput: String) {
        CommandHistoryManager.add(rawInput)

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

                val intent = Intent(context, OverlayService::class.java)

                context.startService(intent)
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
