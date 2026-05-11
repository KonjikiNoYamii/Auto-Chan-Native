package com.silica.assistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object IntentController {

fun openSpotify(context: Context) {
    val packageName = "com.spotify.music"

    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    

    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } else {
        // fallback method
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("spotify:")
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(fallbackIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Gagal membuka Spotify: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
}