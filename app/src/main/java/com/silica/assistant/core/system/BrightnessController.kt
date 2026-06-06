package com.silica.assistant.core.system

import android.content.Context
import android.provider.Settings
import android.util.Log

object BrightnessController {

    private const val TAG = "BrightnessController"

    fun isPermissionGranted(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun increase(context: Context) {

        if (!isPermissionGranted(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted, skipping increase")
            return
        }

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

        if (!isPermissionGranted(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted, skipping decrease")
            return
        }

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
        if (!isPermissionGranted(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted, skipping max")
            return
        }
        setBrightness(context, 255)
    }

    fun min(context: Context) {
        if (!isPermissionGranted(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted, skipping min")
            return
        }
        setBrightness(context, 1)
    }

    private fun setBrightness(
        context: Context,
        value: Int
    ) {

        try {
            val success = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
            if (!success) {
                Log.w(TAG, "putInt returned false for brightness=$value")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException setting brightness", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
        }
    }

    private fun getBrightness(context: Context): Int {

        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                125
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get brightness", e)
            125
        }
    }
}