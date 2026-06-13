package com.silica.assistant.core.action

import com.silica.assistant.core.CommandAliases
import com.silica.assistant.core.model.CommandResult

object ActionMapper {

    fun map(result: CommandResult?): Action {
        if (result == null) return Action.Unknown("null")
        return mapCommand(result.command, result.rawInput)
    }

    private fun mapCommand(command: String, rawInput: String): Action {
        return when (command) {
            // App launchers
            "open_spotify" -> Action.OpenSpotify
            "open_youtube" -> Action.OpenYoutube
            "open_browser" -> Action.OpenBrowser
            "open_settings" -> Action.OpenSettings
            "open_app" -> Action.OpenApp

            // Overlay
            "start_overlay" -> Action.StartOverlay
            "stop_overlay" -> Action.StopOverlay

            // Volume
            "volume_up" -> Action.VolumeUp
            "volume_down" -> Action.VolumeDown
            "mute_volume" -> Action.MuteVolume
            "max_volume" -> Action.MaxVolume

            // Brightness
            "brightness_up" -> Action.BrightnessUp
            "brightness_down" -> Action.BrightnessDown
            "brightness_max" -> Action.BrightnessMax
            "brightness_min" -> Action.BrightnessMin

            // Media
            "media_play_pause" -> Action.MediaPlayPause
            "media_next" -> Action.MediaNext
            "media_previous" -> Action.MediaPrevious

            // SSH
            "ssh_status" -> Action.SshStatus
            "ssh_connect" -> Action.SshConnect
            "ssh_disconnect" -> Action.SshDisconnect
            "laptop_info" -> Action.LaptopInfo(rawInput)

            // AI
            "chat" -> Action.Chat
            "ai_task" -> {
                val context = extractContext(rawInput, "ai_task")
                Action.AiTask(context, rawInput)
            }
            "ai_task_typing" -> Action.AiTaskTyping

            // Debug
            "open_debug" -> Action.OpenDebug

            // Game mode
            "game_mode" -> Action.GameMode
            "stop_game_mode" -> Action.StopGameMode
            "set_game_mode_app" -> Action.SetGameModeApp
            "clear_game_mode_app" -> Action.ClearGameModeApp

            // Screen
            "screen_info" -> Action.ScreenInfo
            "game_comment" -> {
                val context = extractContext(rawInput, "game_comment")
                Action.GameComment(context, rawInput)
            }

            // Accessibility
            "click_element" -> {
                val keyword = rawInput
                    .removePrefix("klik ").removePrefix("tekan ").removePrefix("tap ")
                    .trim()
                Action.ClickElement(keyword, rawInput)
            }
            "scroll_down" -> Action.ScrollDown
            "scroll_up" -> Action.ScrollUp
            "go_back" -> Action.GoBack

            else -> Action.Unknown(rawInput)
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
