package com.silica.assistant.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.widget.ImageView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object CustomAssetManager {

    private const val PREFS_NAME = "custom_assets"
    private const val DIR_NAME = "custom_assets"

    enum class AssetType(val key: String, val defaultResName: String) {
        HEADER("header", "header"),
        ICON("icon", "icon"),
        WAIFU_IDLE("waifu_idle", "mybinik"),
        WAIFU_HAPPY("waifu_happy", "mybinikmangap"),
        WAIFU_LISTENING("waifu_listening", "mybinikmendengarkan"),
        WAIFU_GAME("waifu_game", "icongamemode"),
        CHAT_ICON("chat_icon", "iconchat"),
        POP_SOUND("pop_sound", "pop")
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun assetDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun getCustomPath(context: Context, type: AssetType): String? {
        val path = prefs(context).getString(type.key, null)
        if (path != null && File(path).exists()) return path
        return null
    }

    fun hasCustom(context: Context, type: AssetType): Boolean =
        getCustomPath(context, type) != null

    fun saveCustom(context: Context, type: AssetType, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
            val ext = sourceUri.lastPathSegment?.substringAfterLast('.', "") ?: "dat"
            val destFile = File(assetDir(context), "${type.key}.$ext")
            destFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()
            prefs(context).edit().putString(type.key, destFile.absolutePath).apply()
            true
        } catch (e: Exception) { false }
    }

    fun resetCustom(context: Context, type: AssetType) {
        getCustomPath(context, type)?.let { File(it).delete() }
        prefs(context).edit().remove(type.key).apply()
    }

    fun resetAll(context: Context) {
        assetDir(context).deleteRecursively()
        prefs(context).edit().clear().apply()
    }

    fun loadBitmap(context: Context, type: AssetType): Bitmap? {
        val path = getCustomPath(context, type) ?: return null
        return BitmapFactory.decodeFile(path)
    }

    fun loadImageBitmap(context: Context, type: AssetType): ImageBitmap? {
        return loadBitmap(context, type)?.asImageBitmap()
    }

    fun applyToImageView(context: Context, imageView: ImageView, type: AssetType) {
        val custom = loadBitmap(context, type)
        if (custom != null) {
            imageView.setImageBitmap(custom)
        }
    }

    // greeting customization
    private const val GREETING_MORNING = "greeting_morning"
    private const val GREETING_AFTERNOON = "greeting_afternoon"
    private const val GREETING_EVENING = "greeting_evening"
    private const val GREETING_NIGHT = "greeting_night"

    fun getCustomGreeting(context: Context, hour: Int): String? {
        val key = when {
            hour < 12 -> GREETING_MORNING
            hour < 15 -> GREETING_AFTERNOON
            hour < 18 -> GREETING_EVENING
            else -> GREETING_NIGHT
        }
        return prefs(context).getString(key, null)
    }

    fun getAllCustomGreetings(context: Context): Map<String, String?> {
        return mapOf(
            GREETING_MORNING to prefs(context).getString(GREETING_MORNING, null),
            GREETING_AFTERNOON to prefs(context).getString(GREETING_AFTERNOON, null),
            GREETING_EVENING to prefs(context).getString(GREETING_EVENING, null),
            GREETING_NIGHT to prefs(context).getString(GREETING_NIGHT, null),
        )
    }

    fun saveGreeting(context: Context, period: String, text: String) {
        prefs(context).edit().putString(period, text).apply()
    }

    fun resetGreeting(context: Context, period: String) {
        prefs(context).edit().remove(period).apply()
    }

    fun resetAllGreetings(context: Context) {
        prefs(context).edit()
            .remove(GREETING_MORNING)
            .remove(GREETING_AFTERNOON)
            .remove(GREETING_EVENING)
            .remove(GREETING_NIGHT)
            .apply()
    }
}
