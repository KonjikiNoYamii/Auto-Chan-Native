package com.silica.assistant.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.silica.assistant.core.llm.db.UserFactDao
import com.silica.assistant.core.llm.model.UserFactEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

object CustomAssetManager : KoinComponent {

    private val userFactDao: UserFactDao by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

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
        POP_SOUND("pop_sound", "pop"),
        
        // Voice Assets
        VOICE_MORNING("voice_morning", "ohayougozaimasu"),
        VOICE_AFTERNOON("voice_afternoon", "konnichiwa"),
        VOICE_EVENING("voice_evening", "konbawa"),
        VOICE_NIGHT("voice_night", "konbawa"),
        VOICE_THANKS("voice_thanks", "arigatogozaimasu"),
        VOICE_WELCOME_BACK("voice_welcome_back", "okairinasai"),
        VOICE_YES("voice_yes", "haik"),
        VOICE_YES_HAPPY("voice_yes_happy", "haik_sedikit_senang"),
        VOICE_UNDERSTOOD("voice_understood", "kyoukaishimashita"),
        VOICE_UNDERSTOOD_COLD("voice_understood_cold", "wakarimashita_sedikit_dingin"),
        VOICE_YAMETE("voice_yamete", "bicara_yamete_kudasai_dengancepat_dan_sedikit_dingin"),
        VOICE_ECCHI("voice_ecchi", "ecchinowakiraidesu"),
        VOICE_LAUGH("voice_laugh", "tertawa_kecil"),
        VOICE_GREAT("voice_great", "subarashiidesune"),
        VOICE_RANKUP("voice_rankup", "rankup"),
        VOICE_MISSION_DONE("voice_mission_done", "nozokumade_gokurousamadesu")
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

    // Asset Labels
    fun saveAssetLabel(context: Context, type: AssetType, label: String) {
        prefs(context).edit().putString("label_${type.key}", label).apply()
    }

    fun getAssetLabel(context: Context, type: AssetType): String {
        return prefs(context).getString("label_${type.key}", null) ?: ""
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
        return runBlocking { userFactDao.getFact(key)?.value }
    }

    fun getAllCustomGreetings(context: Context): Map<String, String?> {
        return runBlocking {
            mapOf(
                GREETING_MORNING to userFactDao.getFact(GREETING_MORNING)?.value,
                GREETING_AFTERNOON to userFactDao.getFact(GREETING_AFTERNOON)?.value,
                GREETING_EVENING to userFactDao.getFact(GREETING_EVENING)?.value,
                GREETING_NIGHT to userFactDao.getFact(GREETING_NIGHT)?.value,
            )
        }
    }

    fun saveGreeting(context: Context, period: String, text: String) {
        scope.launch {
            userFactDao.insertFact(UserFactEntity(period, text))
        }
    }

    fun resetGreeting(context: Context, period: String) {
        scope.launch {
            userFactDao.insertFact(UserFactEntity(period, ""))
        }
    }

    fun resetAllGreetings(context: Context) {
        scope.launch {
            userFactDao.insertFact(UserFactEntity(GREETING_MORNING, ""))
            userFactDao.insertFact(UserFactEntity(GREETING_AFTERNOON, ""))
            userFactDao.insertFact(UserFactEntity(GREETING_EVENING, ""))
            userFactDao.insertFact(UserFactEntity(GREETING_NIGHT, ""))
        }
    }
}
