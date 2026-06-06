package com.silica.assistant.overlay

object WaifuStateManager {

    @Volatile
    var currentState: WaifuState =
        WaifuState.RELAX
}