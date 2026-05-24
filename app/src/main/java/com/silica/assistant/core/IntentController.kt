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
            val fallbackIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("spotify:")
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

            try {
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka Spotify: ${e.message}", Toast.LENGTH_LONG)
                        .show()
            }
        }
    }

    fun openApp(context: Context, packageName: String, appName: String) {

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)

        if (intent != null) {

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        } else {

            Toast.makeText(context, "$appName tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    fun openYoutube(context: Context) {

        try {

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        } catch (e: Exception) {

            Toast.makeText(context, "Gagal membuka YouTube", Toast.LENGTH_SHORT).show()
        }
    }

    fun openBrowser(context: Context) {

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun openSettings(context: Context) {

        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun searchGoogle(context: Context, query: String) {

        val encodedQuery = Uri.encode(query)

        val intent =
                Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=$encodedQuery")
                )

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}
