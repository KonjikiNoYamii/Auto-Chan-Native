package com.silica.assistant.core.action

import com.silica.assistant.core.CommandAliases
import com.silica.assistant.core.model.CommandResult
import com.silica.assistant.core.WakeWord

object ActionMapper {

    private fun stripWakeWord(input: String): String {
        val lower = input.lowercase().trim()
        for (alias in WakeWord.aliases) {
            if (lower.startsWith(alias)) {
                return input.trim().removePrefix(alias).trim()
            }
        }
        return input.trim()
    }

    fun map(result: CommandResult?): Action {
        if (result == null) return Action.Unknown("null")
        return mapCommand(result.command, stripWakeWord(result.rawInput))
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
            "click_region" -> {
                val region = rawInput
                    .removePrefix("klik ").removePrefix("tap ")
                    .trim()
                Action.ClickRegion(region)
            }
            "scroll_down" -> Action.ScrollDown
            "scroll_up" -> Action.ScrollUp
            "swipe_direction" -> {
                val dir = rawInput
                    .removePrefix("geser ").removePrefix("swipe ").removePrefix("slide ")
                    .trim()
                Action.SwipeDirection(dir)
            }
            "go_back" -> Action.GoBack
            "type_text" -> {
                val text = rawInput
                    .removePrefix("ketik ").removePrefix("tulis ").removePrefix("type ")
                    .trim()
                Action.TypeText(text)
            }
            "type_into" -> {
                val parts = rawInput
                    .removePrefix("isi ").removePrefix("ketik di ").trim()
                val denganIdx = parts.indexOf(" dengan ")
                val colonIdx = parts.indexOf(": ")
                val separator = if (denganIdx >= 0) denganIdx else if (colonIdx >= 0) colonIdx else -1
                if (separator >= 0) {
                    val field = parts.substring(0, separator).trim()
                    val text = parts.substring(separator + 1).removePrefix("dengan ").removePrefix(": ").trim()
                    Action.TypeInto(text, field)
                } else {
                    Action.TypeText(parts)
                }
            }
            "long_press" -> Action.LongPress(0, 0, 600)
            "wait_for_text" -> {
                val text = rawInput
                    .removePrefix("tunggu ").removePrefix("tunggu teks ").removePrefix("cari teks ")
                    .removePrefix("wait for ").trim()
                Action.WaitForText(text)
            }

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
