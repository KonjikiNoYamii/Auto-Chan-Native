package com.silica.assistant.core.system

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.silica.assistant.R
import com.silica.assistant.core.CustomAssetManager
import android.util.Log

object SoundManager {
    private var soundPool: SoundPool? = null
    private var sounds = mutableMapOf<String, Int>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attributes)
            .build()
            
        soundPool?.let { pool ->
            sounds["pop"] = pool.load(context, R.raw.pop, 1)
            sounds["level_up"] = pool.load(context, R.raw.level_up, 1)
            sounds["quest_done"] = pool.load(context, R.raw.quest_done, 1)
            
            // Load default voices
            loadDefaultVoices(context, pool)
        }
        isInitialized = true
    }

    private fun loadDefaultVoices(context: Context, pool: SoundPool) {
        CustomAssetManager.AssetType.values().forEach { type ->
            if (type.key.startsWith("voice_")) {
                val resId = context.resources.getIdentifier(type.defaultResName, "raw", context.packageName)
                if (resId != 0) {
                    sounds[type.key] = pool.load(context, resId, 1)
                }
            }
        }
    }

    fun playSound(name: String) {
        val soundId = sounds[name] ?: return
        soundPool?.play(soundId, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun playVoice(context: Context, type: CustomAssetManager.AssetType) {
        val customPath = CustomAssetManager.getCustomPath(context, type)
        if (customPath != null) {
            // Play custom audio file
            try {
                val pool = soundPool ?: return
                val soundId = pool.load(customPath, 1)
                pool.setOnLoadCompleteListener { p, id, status ->
                    if (id == soundId && status == 0) {
                        p.play(id, 0.8f, 0.8f, 1, 0, 1f)
                    }
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Error playing custom voice: ${e.message}")
            }
        } else {
            // Play default
            playSound(type.key)
        }
    }

    fun playLevelUp() = playSound("level_up")
    fun playQuestComplete() = playSound("quest_done")
    fun playChime() = playSound("pop") // Pop works well for subtle chime

    fun release() {
        soundPool?.release()
        soundPool = null
        sounds.clear()
        isInitialized = false
    }
}
