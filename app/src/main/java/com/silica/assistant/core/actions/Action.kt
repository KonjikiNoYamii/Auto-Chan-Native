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

    // Accessibility - Gesture
    data class ClickCoordinate(val x: Int, val y: Int) : Action()
    data class ClickRegion(val region: String) : Action()
    data class SwipeGesture(
        val fromX: Float, val fromY: Float,
        val toX: Float, val toY: Float,
        val durationMs: Long = 400
    ) : Action()
    data class SwipeDirection(val direction: String) : Action()
    data class LongPress(val x: Int, val y: Int, val durationMs: Long = 600) : Action()

    // Accessibility - Type
    data class TypeText(val text: String) : Action()
    data class TypeInto(val text: String, val fieldHint: String) : Action()

    // Accessibility - Wait
    data class WaitForText(val text: String, val timeoutMs: Long = 5000) : Action()

    // Macro
    data class Macro(val steps: List<Action>, val description: String = "") : Action()

    // Existing
    data class ClickElement(val keyword: String, val rawInput: String) : Action()
    object ScrollDown : Action()
    object ScrollUp : Action()
    object GoBack : Action()

    data class Unknown(val raw: String) : Action()
}
