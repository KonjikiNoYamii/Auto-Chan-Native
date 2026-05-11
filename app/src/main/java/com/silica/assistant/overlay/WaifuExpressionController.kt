package com.silica.assistant.overlay

import android.widget.ImageView
import com.silica.assistant.R

class WaifuExpressionController(
    private val imageView: ImageView
) {

    private var currentState: WaifuState = WaifuState.IDLE

    fun setState(state: WaifuState) {
        if (currentState == state) return

        currentState = state
        update()
    }

    private fun update() {
        val resId = when (currentState) {

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