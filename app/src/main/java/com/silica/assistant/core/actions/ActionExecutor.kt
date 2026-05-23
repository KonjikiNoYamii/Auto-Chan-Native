package com.silica.assistant.core.action

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.silica.assistant.service.OverlayService
import com.silica.assistant.core.IntentController

object ActionExecutor {

    fun execute(context: Context, action: Action) {

        when (action) {

            is Action.OpenSpotify -> {
                IntentController.openSpotify(context)
            }

            is Action.OpenYoutube -> {
                IntentController.openYoutube(context)
            }

            is Action.OpenBrowser -> {
                IntentController.openBrowser(context)
            }

            is Action.OpenSettings -> {
                IntentController.openSettings(context)
            }

            is Action.StartOverlay -> {
                val intent = Intent(context, OverlayService::class.java)
                context.startService(intent)
            }

            is Action.Unknown -> {
                Toast.makeText(
                    context,
                    "Unknown action: ${action.raw}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}