package com.silica.assistant.overlay

import android.content.Context
import android.widget.ImageView
import com.silica.assistant.R
import com.silica.assistant.core.CustomAssetManager

import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation

class WaifuExpressionController(
    private val imageView: ImageView,
    private val context: Context
) {
    private var lastState: WaifuState? = null

    fun update() {
        // Force GAME state when game mode is active
        val state = if (GameModeManager.isGameMode) WaifuState.GAME else WaifuStateManager.currentState
        
        if (state == lastState) return
        lastState = state

        if (state == WaifuState.GAME) {
            imageView.alpha = 0.55f
            if (CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_GAME)) {
                CustomAssetManager.applyToImageView(context, imageView, CustomAssetManager.AssetType.WAIFU_GAME)
            } else {
                imageView.setImageResource(R.drawable.icongamemode)
            }
            return
        }

        imageView.alpha = 1.0f

        val type = when (state) {
            WaifuState.RELAX -> CustomAssetManager.AssetType.WAIFU_IDLE
            WaifuState.TALK -> CustomAssetManager.AssetType.WAIFU_HAPPY
            WaifuState.LISTEN -> CustomAssetManager.AssetType.WAIFU_LISTENING
            else -> return
        }

        if (CustomAssetManager.hasCustom(context, type)) {
            CustomAssetManager.applyToImageView(context, imageView, type)
        } else {
            val resId = when (state) {
                WaifuState.RELAX -> R.drawable.mybinik
                WaifuState.TALK -> R.drawable.mybinikmangap
                WaifuState.LISTEN -> R.drawable.mybinikmendengarkan
                else -> return
            }
            imageView.setImageResource(resId)
        }
    }

    fun shake() {
        val shake = TranslateAnimation(0f, 10f, 0f, 0f).apply {
            duration = 500
            interpolator = CycleInterpolator(5f)
        }
        imageView.startAnimation(shake)
    }
}