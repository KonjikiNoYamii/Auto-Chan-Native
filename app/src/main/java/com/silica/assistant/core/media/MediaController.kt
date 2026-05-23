package com.silica.assistant.core.media

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

object MediaController {

    private fun sendKey(context: Context, keyCode: Int) {

        val audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )

        audioManager.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    fun playPause(context: Context) {
        sendKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(context: Context) {
        sendKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(context: Context) {
        sendKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
}