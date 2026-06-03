package com.silica.assistant.service

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.silica.assistant.R
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.llm.YamiQuotes
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.overlay.WaifuExpressionController
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import kotlin.random.Random

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

    // random Yami quotes
    private val randomQuoteHandler = Handler(Looper.getMainLooper())
    private var isQuoteScheduled = false
    private var lastTouchTime = 0L
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    randomQuoteHandler.removeCallbacksAndMessages(null)
                    isQuoteScheduled = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    scheduleRandomQuote()
                }
            }
        }
    }

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

        controller = WaifuExpressionController(waifuImage, this)

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
                    if (listening) WaifuState.LISTEN else WaifuState.RELAX
        }

        WaifuStateManager.currentState = WaifuState.RELAX

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

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        scheduleRandomQuote()
    }

    private fun isIdle(): Boolean {
        if (!screenOn) return false
        val idleTime = System.currentTimeMillis() - lastTouchTime
        return idleTime > 30_000
    }

    private fun scheduleRandomQuote() {
        if (isQuoteScheduled || !screenOn) return
        isQuoteScheduled = true
        val delay = if (isIdle()) {
            Random.nextLong(15_000, 30_000)
        } else {
            Random.nextLong(1_200_000, 2_400_000)
        }
        randomQuoteHandler.postDelayed({
            showRandomQuote()
            isQuoteScheduled = false
            scheduleRandomQuote()
        }, delay)
    }

    private fun showRandomQuote() {
        if (!::bubbleText.isInitialized || overlayView.windowToken == null) return

        val (text, emotion) = YamiQuotes.random()

        WaifuStateManager.currentState = when (emotion) {
            "happy", "blush" -> WaifuState.TALK
            else -> WaifuState.RELAX
        }

        showBubble(text)
    }

    // =========================
    // TOUCH SYSTEM
    // =========================
    private fun setupTouchListener() {

        overlayView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    lastTouchTime = System.currentTimeMillis()
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
                                startVoiceWithPermissionCheck()
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

                    WaifuStateManager.currentState = WaifuState.RELAX

                    windowManager.updateViewLayout(overlayView, params)

                    true
                }
                MotionEvent.ACTION_UP -> {

                    longPressHandler.removeCallbacksAndMessages(null)

                    if (!isMoving && !longPressTriggered) {

                        // toggle voice via SINGLE SYSTEM
                        startVoiceWithPermissionCheck()
                    }

                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            VoiceManager.start()
        } else {
            showBubble("Izinkan akses mikrofon dulu ya")
            OverlayEventBus.navigateScreen.value = "request_audio_permission"
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                startActivity(launchIntent)
            }
        }
    }

    // =========================
    // BUBBLE UI
    // =========================
    private fun playPopSound() {

        try {
            popPlayer?.release()

            val customPop = CustomAssetManager.getCustomPath(this, CustomAssetManager.AssetType.POP_SOUND)
            popPlayer = if (customPop != null) {
                MediaPlayer().apply {
                    setDataSource(customPop)
                    prepare()
                }
            } else {
                MediaPlayer.create(this, R.raw.pop)
            }
            popPlayer?.setVolume(0.6f, 0.6f)
            popPlayer?.start()
            popPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showBubble(text: String) {

        handler.post {

            if (!::bubbleText.isInitialized) return@post

            bubbleText.text = text
            bubbleText.visibility = View.VISIBLE

            playPopSound()

            val duration = (2000L + text.length * 50L).coerceAtMost(8000L)

            bubbleHideRunnable?.let { handler.removeCallbacks(it) }

            bubbleHideRunnable = Runnable { bubbleText.visibility = View.GONE }

            handler.postDelayed(bubbleHideRunnable!!, duration)
        }
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
        randomQuoteHandler.removeCallbacksAndMessages(null)
        isQuoteScheduled = false
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}

        VoiceManager.destroy()

        popPlayer?.release()
        popPlayer = null

        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
