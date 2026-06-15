package com.silica.assistant.overlay

import android.animation.ObjectAnimator
import android.content.Context
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import com.silica.assistant.R
import com.silica.assistant.core.CustomAssetManager

class WaifuExpressionController(
    private val imageView: ImageView,
    private val context: Context
) {
    private var lastState: WaifuState? = null
    private var floatingAnim: ObjectAnimator? = null
    private var isFading = false

    fun startFloating() {
        floatingAnim?.cancel()
        floatingAnim = ObjectAnimator.ofFloat(imageView, "translationY", 0f, -8f).apply {
            duration = 2500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    fun stopFloating() {
        floatingAnim?.cancel()
        floatingAnim = null
        imageView.translationY = 0f
    }

    fun update() {
        val state = if (GameModeManager.isGameMode) WaifuState.GAME else WaifuStateManager.currentState

        if (state == lastState) return
        lastState = state

        val isGame = state == WaifuState.GAME
        val targetAlpha = if (isGame) 0.55f else 1.0f

        if (isFading) return

        isFading = true
        imageView.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                if (isGame) {
                    if (CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_GAME)) {
                        CustomAssetManager.applyToImageView(context, imageView, CustomAssetManager.AssetType.WAIFU_GAME)
                    } else {
                        imageView.setImageResource(R.drawable.icongamemode)
                    }
                } else {
                    val type = when (state) {
                        WaifuState.RELAX -> CustomAssetManager.AssetType.WAIFU_IDLE
                        WaifuState.TALK -> CustomAssetManager.AssetType.WAIFU_HAPPY
                        WaifuState.LISTEN -> CustomAssetManager.AssetType.WAIFU_LISTENING
                        else -> return@withEndAction
                    }
                    if (CustomAssetManager.hasCustom(context, type)) {
                        CustomAssetManager.applyToImageView(context, imageView, type)
                    } else {
                        val resId = when (state) {
                            WaifuState.RELAX -> R.drawable.mybinik
                            WaifuState.TALK -> R.drawable.mybinikmangap
                            WaifuState.LISTEN -> R.drawable.mybinikmendengarkan
                            else -> return@withEndAction
                        }
                        imageView.setImageResource(resId)
                    }
                }
                imageView.animate()
                    .alpha(targetAlpha)
                    .setDuration(180)
                    .withEndAction { isFading = false }
                    .start()
            }
            .start()
    }

    fun shake() {
        val shake = TranslateAnimation(0f, 10f, 0f, 0f).apply {
            duration = 500
            interpolator = CycleInterpolator(5f)
        }
        imageView.startAnimation(shake)
    }
}
