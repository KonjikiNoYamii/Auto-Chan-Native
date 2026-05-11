package com.silica.assistant.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.silica.assistant.R
import com.silica.assistant.overlay.WaifuExpressionController
import com.silica.assistant.overlay.WaifuState

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var waifuImage: ImageView
    private lateinit var controller: WaifuExpressionController

    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var isMoving = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        waifuImage = overlayView.findViewById(R.id.waifuImage)
        controller = WaifuExpressionController(waifuImage)

        setState(WaifuState.IDLE)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        overlayView.setOnTouchListener { _, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    isMoving = false

                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY

                    setState(WaifuState.LISTENING)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (dx * dx + dy * dy > 400) {
                        isMoving = true
                        setState(WaifuState.LISTENING)
                    }

                    params.x = initialX + dx
                    params.y = initialY + dy

                    windowManager.updateViewLayout(overlayView, params)

                    true
                }

                MotionEvent.ACTION_UP -> {

                    if (!isMoving) {
                        setState(WaifuState.HAPPY)

                        handler.postDelayed({
                            setState(WaifuState.IDLE)
                        }, 800)

                    } else {
                        setState(WaifuState.IDLE)
                    }

                    true
                }

                else -> false
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun setState(state: WaifuState) {
        controller.setState(state)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}