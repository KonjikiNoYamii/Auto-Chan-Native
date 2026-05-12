package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.silica.assistant.service.OverlayService

object CommandManager {

    fun execute(context: Context, rawInput: String) {

        val command = CommandNormalizer.normalize(rawInput)

        if (command == null) {

            Toast.makeText(context, "Command not recognized", Toast.LENGTH_SHORT).show()

            return
        }
        when (command) {
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
            else -> {
                Toast.makeText(context, "Command handler missing: $command", Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }
}
