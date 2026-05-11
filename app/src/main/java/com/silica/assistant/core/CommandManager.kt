package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import com.silica.assistant.service.OverlayService

object CommandManager {

    fun execute(context: Context, command: String) {
        when (command) {

            "open_spotify" -> {
                IntentController.openSpotify(context)
            }
            "start_overlay" -> {
                val intent = Intent(context, OverlayService::class.java)
                context.startService(intent)
            }

            else -> {
                android.widget.Toast.makeText(
                    context,
                    "Unknown command: $command",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}