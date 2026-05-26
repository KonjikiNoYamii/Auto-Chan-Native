package com.silica.assistant.core.system

import android.content.Context
import android.media.AudioManager

object VolumeController {

    fun volumeUp(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val step = (max * 0.2).toInt().coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current + step).coerceAtMost(max)

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            next,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun volumeDown(context: Context) {

        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val step = (max * 0.2).toInt().coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current - step).coerceAtLeast(0)

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            next,
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