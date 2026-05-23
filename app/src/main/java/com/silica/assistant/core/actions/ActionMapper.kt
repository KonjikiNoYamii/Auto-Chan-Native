package com.silica.assistant.core.action

import com.silica.assistant.core.model.CommandResult

object ActionMapper {

    fun map(result: CommandResult?): Action {

        if (result == null) return Action.Unknown("null")

        return when (result.command) {

            "open_spotify" -> Action.OpenSpotify
            "open_youtube" -> Action.OpenYoutube
            "open_browser" -> Action.OpenBrowser
            "open_settings" -> Action.OpenSettings
            "start_overlay" -> Action.StartOverlay

            else -> Action.Unknown(result.rawInput)
        }
    }
}