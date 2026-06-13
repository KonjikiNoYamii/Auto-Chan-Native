package com.silica.assistant.core.action

sealed class Action {

    // App launchers
    object OpenSpotify : Action()
    object OpenYoutube : Action()
    object OpenBrowser : Action()
    object OpenSettings : Action()
    object OpenApp : Action()

    // Overlay
    object StartOverlay : Action()
    object StopOverlay : Action()

    // Volume
    object VolumeUp : Action()
    object VolumeDown : Action()
    object MuteVolume : Action()
    object MaxVolume : Action()

    // Brightness
    object BrightnessUp : Action()
    object BrightnessDown : Action()
    object BrightnessMax : Action()
    object BrightnessMin : Action()

    // Media
    object MediaPlayPause : Action()
    object MediaNext : Action()
    object MediaPrevious : Action()

    // SSH
    object SshStatus : Action()
    object SshConnect : Action()
    object SshDisconnect : Action()
    data class LaptopInfo(val rawInput: String) : Action()

    // AI
    object Chat : Action()
    object AiTaskTyping : Action()
    data class AiTask(val context: String, val rawInput: String) : Action()

    // Debug
    object OpenDebug : Action()

    // Game mode
    object GameMode : Action()
    object StopGameMode : Action()
    object SetGameModeApp : Action()
    object ClearGameModeApp : Action()

    // Screen
    object ScreenInfo : Action()
    data class GameComment(val context: String, val rawInput: String) : Action()

    // Accessibility
    data class ClickElement(val keyword: String, val rawInput: String) : Action()
    object ScrollDown : Action()
    object ScrollUp : Action()
    object GoBack : Action()

    data class Unknown(val raw: String) : Action()
}
