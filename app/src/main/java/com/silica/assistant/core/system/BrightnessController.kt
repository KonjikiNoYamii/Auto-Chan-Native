package com.silica.assistant.core.system

import android.content.Context
import android.provider.Settings

object BrightnessController {

    fun increase(context: Context) {

        val current = getBrightness(context)

        val step = when {
            current < 50 -> 15
            current < 120 -> 25
            current < 200 -> 35
            else -> 20
        }

        val next = (current + step).coerceAtMost(255)

        setBrightness(context, next)
    }

    fun decrease(context: Context) {

        val current = getBrightness(context)

        val step = when {
            current < 50 -> 10
            current < 120 -> 20
            current < 200 -> 30
            else -> 35
        }

        val next = (current - step).coerceAtLeast(1)

        setBrightness(context, next)
    }

    fun max(context: Context) {
        setBrightness(context, 255)
    }

    fun min(context: Context) {
        setBrightness(context, 1)
    }

    private fun setBrightness(
        context: Context,
        value: Int
    ) {

        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            value
        )
    }

    private fun getBrightness(context: Context): Int {

        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            125
        )
    }
}