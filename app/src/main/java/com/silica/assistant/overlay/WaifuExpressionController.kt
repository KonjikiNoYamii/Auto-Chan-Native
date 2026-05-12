package com.silica.assistant.overlay

import android.widget.ImageView
import com.silica.assistant.R

class WaifuExpressionController(
    private val imageView: ImageView
) {

    fun update() {

        val resId = when (
            WaifuStateManager.currentState
        ) {

            WaifuState.IDLE ->
                R.drawable.mybinik

            WaifuState.HAPPY ->
                R.drawable.mybinikmangap

            WaifuState.LISTENING ->
                R.drawable.mybinikmendengarkan
        }

        imageView.setImageResource(resId)
    }
}