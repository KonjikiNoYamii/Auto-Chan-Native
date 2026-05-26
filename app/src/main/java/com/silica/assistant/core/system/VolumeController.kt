package com.silica.assistant.core.system

import android.content.Context
import android.media.AudioManager

object VolumeController {

    fun volumeUp(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun volumeDown(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun mute(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun maxVolume(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        val max =
            audioManager.getStreamMaxVolume(
                AudioManager.STREAM_MUSIC
            )

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            max,
            AudioManager.FLAG_SHOW_UI
        )
    }
}