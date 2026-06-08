package com.silica.assistant.core.action

sealed class Action {

    object OpenSpotify : Action()
    object OpenYoutube : Action()
    object OpenBrowser : Action()
    object OpenSettings : Action()

    object StartOverlay : Action()

    object VolumeUp : Action()
    object VolumeDown : Action()
    object MuteVolume : Action()
    object MaxVolume : Action()

    object ScreenInfo : Action()
    data class ClickElement(val text: String) : Action()
    object ScrollDown : Action()
    object ScrollUp : Action()
    object GoBack : Action()

    data class Unknown(val raw: String) : Action()
}