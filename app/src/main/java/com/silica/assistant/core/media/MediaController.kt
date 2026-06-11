package com.silica.assistant.core.media

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
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

    fun playFromSearch(context: Context, query: String) {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // fallback if no media app handles it
            val searchIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com/search/${android.net.Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
        }
    }
}
