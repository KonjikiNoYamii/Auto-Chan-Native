package com.silica.assistant.core.action

sealed class Action {

    object OpenSpotify : Action()
    object OpenYoutube : Action()
    object OpenBrowser : Action()
    object OpenSettings : Action()

    object StartOverlay : Action()

    data class Unknown(val raw: String) : Action()
}