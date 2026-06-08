package com.silica.assistant.core.action

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.silica.assistant.core.IntentController
import com.silica.assistant.core.system.VolumeController
import com.silica.assistant.service.OverlayService

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
            is Action.VolumeUp -> {
                VolumeController.volumeUp(context)
            }
            is Action.VolumeDown -> {
                VolumeController.volumeDown(context)
            }
            is Action.MuteVolume -> {
                VolumeController.mute(context)
            }
            is Action.MaxVolume -> {
                VolumeController.maxVolume(context)
            }
            is Action.StartOverlay -> {
                val intent = Intent(context, OverlayService::class.java)
                context.startService(intent)
            }
            is Action.ScreenInfo -> {}
            is Action.ClickElement -> {}
            is Action.ScrollDown -> {}
            is Action.ScrollUp -> {}
            is Action.GoBack -> {}
            is Action.Unknown -> {
                Toast.makeText(context, "Unknown action: ${action.raw}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
