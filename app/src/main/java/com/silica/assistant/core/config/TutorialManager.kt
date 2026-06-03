package com.silica.assistant.core.config

import android.content.Context

object TutorialManager {

    private const val PREFS_NAME = "tutorial_prefs"
    private const val KEY_OVERLAY_TUTORIAL_DONE = "overlay_tutorial_done"

    fun isOverlayTutorialDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_TUTORIAL_DONE, false)
    }

    fun markOverlayTutorialDone(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OVERLAY_TUTORIAL_DONE, true)
            .apply()
    }
}
