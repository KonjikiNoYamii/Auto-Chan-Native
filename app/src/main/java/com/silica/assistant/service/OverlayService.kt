package com.silica.assistant.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.silica.assistant.R
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.overlay.WaifuExpressionController
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var waifuImage: ImageView
    private lateinit var bubbleText: TextView

    private lateinit var controller: WaifuExpressionController

    private var popPlayer: MediaPlayer? = null

    private var isMoving = false
    private var longPressTriggered = false

    private var initialX = 0
    private var initialY = 0

    private var touchX = 0f
    private var touchY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val longPressHandler = Handler(Looper.getMainLooper())

    private var bubbleHideRunnable: Runnable? = null

    // 🧠 expression loop
    private val expressionUpdater =
            object : Runnable {
                override fun run() {

                    if (::controller.isInitialized && overlayView.windowToken != null) {
                        controller.update()
                    }

                    handler.postDelayed(this, 500)
                }
            }

    override fun onCreate() {
        super.onCreate()

        val intent = Intent(this, VoiceForegroundService::class.java)
        startForegroundService(intent)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        waifuImage = overlayView.findViewById(R.id.waifuImage)
        bubbleText = overlayView.findViewById(R.id.bubbleText)

        controller = WaifuExpressionController(waifuImage)

        handler.post(expressionUpdater)

        OverlayEventBus.onBubble = { text -> showBubble(text) }

        // 🧠 VOICE SYSTEM (ONLY ONE SOURCE)
        VoiceManager.init(this)

        VoiceManager.onResult = { text ->
            OverlayEventBus.onBubble?.invoke("🎤 $text")
            CommandManager.execute(this, text)
        }

        VoiceManager.onStateChange = { listening ->
            WaifuStateManager.currentState =
                    if (listening) WaifuState.LISTENING else WaifuState.IDLE
        }

        WaifuStateManager.currentState = WaifuState.IDLE

        params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                )

        setupTouchListener()

        windowManager.addView(overlayView, params)
    }

    // =========================
    // TOUCH SYSTEM
    // =========================
    private fun setupTouchListener() {

        overlayView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    isMoving = false
                    longPressTriggered = false

                    initialX = params.x
                    initialY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    // long press → voice start
                    longPressHandler.postDelayed(
                            {
                                longPressTriggered = true
                                VoiceManager.start()
                            },
                            600
                    )

                    true
                }
                MotionEvent.ACTION_MOVE -> {

                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (dx * dx + dy * dy > 400) {
                        isMoving = true
                        longPressHandler.removeCallbacksAndMessages(null)
                    }

                    params.x = initialX + dx
                    params.y = initialY + dy

                    WaifuStateManager.currentState = WaifuState.IDLE

                    windowManager.updateViewLayout(overlayView, params)

                    true
                }
                MotionEvent.ACTION_UP -> {

                    longPressHandler.removeCallbacksAndMessages(null)

                    if (!isMoving && !longPressTriggered) {

                        // toggle voice via SINGLE SYSTEM
                        VoiceManager.start()
                    }

                    true
                }
                else -> false
            }
        }
    }

    // =========================
    // BUBBLE UI
    // =========================
    private fun playPopSound() {

        try {
            popPlayer?.release()

            popPlayer = MediaPlayer.create(this, R.raw.pop)
            popPlayer?.setVolume(0.6f, 0.6f)
            popPlayer?.start()

            popPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showBubble(text: String) {

        if (!::bubbleText.isInitialized) return

        bubbleText.text = text
        bubbleText.visibility = View.VISIBLE

        playPopSound()

        val duration = (2000L + text.length * 50L).coerceAtMost(8000L)

        bubbleHideRunnable?.let { handler.removeCallbacks(it) }

        bubbleHideRunnable = Runnable { bubbleText.visibility = View.GONE }

        handler.postDelayed(bubbleHideRunnable!!, duration)
    }

    // =========================
    // CLEANUP
    // =========================
    override fun onDestroy() {
        super.onDestroy()

        OverlayEventBus.onBubble = null
        VoiceManager.onResult = null
        VoiceManager.onStateChange = null

        handler.removeCallbacks(expressionUpdater)
        longPressHandler.removeCallbacksAndMessages(null)

        VoiceManager.destroy()

        popPlayer?.release()
        popPlayer = null

        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
