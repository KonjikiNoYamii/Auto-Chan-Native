package com.silica.assistant.core.system

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.silica.assistant.R

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
        }
        isInitialized = true
    }

    fun playSound(name: String) {
        val soundId = sounds[name] ?: return
        soundPool?.play(soundId, 0.7f, 0.7f, 1, 0, 1f)
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
