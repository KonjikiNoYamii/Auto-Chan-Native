package com.silica.assistant.service

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.media.MediaPlayer
import android.util.DisplayMetrics
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.silica.assistant.R
import com.silica.assistant.core.ActivityDetector
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.llm.WaifuNotifier
import com.silica.assistant.core.llm.YamiQuotes
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.overlay.GameModeManager
import com.silica.assistant.overlay.WaifuExpressionController
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
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

    private var displayWidth = 0
    private var displayHeight = 0
    private var waifuWidth = 0
    private var waifuHeight = 0

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

    private val activityScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var activityDetector: ActivityDetector
    private var lastDetectedApp: String? = null
    private var lastCommentTime = 0L
    private var detecting = false

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

                        val gameReq = OverlayEventBus.gameModeRequest
                        if (gameReq != null) {
                            OverlayEventBus.gameModeRequest = null
                            if (gameReq) {
                                val den = if (waifuWidth > 0) waifuWidth / 120f else 2f
                                GameModeManager.enterGameMode(this@OverlayService, params.x, params.y, displayWidth, displayHeight, den)
                                val half = (60 * den).toInt()
                                params.gravity = Gravity.TOP or Gravity.START
                                params.x = displayWidth / 2 - half
                                params.y = 0
                                randomQuoteHandler.removeCallbacksAndMessages(null)
                                isQuoteScheduled = false
                                windowManager.updateViewLayout(overlayView, params)
                            } else {
                                val (restoreX, restoreY) = GameModeManager.exitGameMode()
                                params.x = restoreX
                                params.y = restoreY
                                windowManager.updateViewLayout(overlayView, params)
                            }
                        }

                        val metrics = DisplayMetrics()
                        windowManager.defaultDisplay.getMetrics(metrics)
                        val w = metrics.widthPixels
                        val h = metrics.heightPixels
                        if (w != displayWidth || h != displayHeight) {
                            displayWidth = w
                            displayHeight = h
                            val vw = overlayView.width.coerceAtLeast(waifuWidth)
                            val vh = overlayView.height.coerceAtLeast(waifuHeight)
                            val topBound = -(16 * metrics.density).toInt()
                            val maxX = (w - vw).coerceAtLeast(0)
                            val maxY = (h - vh).coerceAtLeast(0)
                            params.x = params.x.coerceIn(0, maxX)
                            params.y = params.y.coerceIn(topBound, maxY)
                            windowManager.updateViewLayout(overlayView, params)
                        }
                    }

                    handler.postDelayed(this, 500)
                }
            }

    override fun onCreate() {
        super.onCreate()

        try {
            val intent = Intent(this, VoiceForegroundService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        waifuWidth = (120 * metrics.density).toInt()
        waifuHeight = (120 * metrics.density).toInt()

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        waifuImage = overlayView.findViewById(R.id.waifuImage)
        bubbleText = overlayView.findViewById(R.id.bubbleText)

        controller = WaifuExpressionController(waifuImage, this)

        handler.post(expressionUpdater)

        OverlayEventBus.onBubble = { text -> showBubble(text) }

        // 🧠 VOICE SYSTEM (ONLY ONE SOURCE)
        VoiceManager.init(this)
        WaifuNotifier.init(this)

        VoiceManager.onResult = { text ->
            OverlayEventBus.onBubble?.invoke("🎤 $text")
            CommandManager.execute(this, text)
        }

        VoiceManager.onStateChange = { listening ->
            if (!GameModeManager.isGameMode) {
                WaifuStateManager.currentState =
                        if (listening) WaifuState.LISTEN else WaifuState.RELAX
            }
        }

        WaifuStateManager.currentState = WaifuState.RELAX

        val overlayType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE

        params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        overlayType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

        setupTouchListener()

        windowManager.addView(overlayView, params)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        scheduleRandomQuote()

        activityDetector = ActivityDetector(this)
        detecting = activityDetector.isUsageStatsGranted()
        activityScope.launch { detectActivityLoop() }

        // Auto-redirect ke Settings kalau izin belum dikasih
        if (!detecting) {
            handler.postDelayed({
                showBubble("🔒 Aktifkan Akses Penggunaan Aplikasi untuk mode game")
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                    )
                } catch (_: Exception) {}
            }, 2000)
        }
    }

    private fun isIdle(): Boolean {
        if (!screenOn) return false
        val idleTime = System.currentTimeMillis() - lastTouchTime
        return idleTime > 30_000
    }

    private fun scheduleRandomQuote() {
        if (isQuoteScheduled || !screenOn || GameModeManager.isGameMode) return
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

        // sometimes also push a notification
        if (screenOn && kotlin.random.Random.nextInt(3) == 0) {
            WaifuNotifier.showRandomNotification()
        }
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

                    // hide bubble during drag
                    if (bubbleText.visibility == View.VISIBLE) {
                        bubbleText.visibility = View.GONE
                        bubbleHideRunnable?.let { handler.removeCallbacks(it) }
                    }

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

                    if (!GameModeManager.isGameMode) {
                        WaifuStateManager.currentState = WaifuState.RELAX
                    }

                    windowManager.updateViewLayout(overlayView, params)

                    true
                }
                MotionEvent.ACTION_UP -> {

                    longPressHandler.removeCallbacksAndMessages(null)

                    if (!isMoving && !longPressTriggered) {
                        startVoiceWithPermissionCheck()
                    } else if (isMoving) {
                        // 🧲 snap to nearest edge
                        val density = resources.displayMetrics.density
                        val snapThreshold = (80 * density).toInt()
                        val edgeMargin = (2 * density).toInt()
                        val cx = params.x + waifuWidth / 2
                        val cy = params.y + waifuHeight / 2
                        val distLeft = cx
                        val distRight = displayWidth - cx
                        val distTop = cy
                        val distBottom = displayHeight - cy
                        val minDist = minOf(distLeft, distRight, distTop, distBottom)
                        if (minDist < snapThreshold) {
                            when (minDist) {
                                distLeft -> params.x = edgeMargin
                                distRight -> params.x = displayWidth - waifuWidth - edgeMargin
                                distTop -> params.y = edgeMargin
                                distBottom -> params.y = displayHeight - waifuHeight - edgeMargin
                            }
                            windowManager.updateViewLayout(overlayView, params)
                        }
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
    // ACTIVITY DETECTION & GAME MODE
    // =========================
    private suspend fun detectActivityLoop() {
        while (true) {
            val granted = activityDetector.isUsageStatsGranted()
            if (granted && !detecting) {
                detecting = true
                handler.post {
                    showBubble("✅ Akses penggunaan aplikasi diberikan — mode game siap")
                }
            } else if (!granted && detecting) {
                detecting = false
                handler.post {
                    showBubble("🔒 Akses penggunaan aplikasi belum diizinkan — mode game tidak aktif")
                }
            }

            if (!detecting) {
                delay(3000)
                continue
            }

            val pkg = activityDetector.getForegroundApp()
            if (pkg != null && pkg != lastDetectedApp && pkg != packageName) {
                lastDetectedApp = pkg
                val appName = GameModeManager.getAppName(this, pkg)
                val isGame = GameModeManager.isGame(this, pkg)
                GameModeManager.currentAppPackage = pkg
                GameModeManager.currentAppName = appName

                if (GameModeManager.manualMode) {
                    // skip auto mode when manual override is active
                } else {
                    val isGameModeApp = pkg == GameModeManager.gameModeAppPackage
                    if ((isGameModeApp || isGame) && !GameModeManager.isGameMode) {
                        val den = if (waifuWidth > 0) waifuWidth / 120f else 2f
                        GameModeManager.enterGameMode(this, params.x, params.y, displayWidth, displayHeight, den, auto = true)
                        handler.post {
                            params.gravity = Gravity.TOP or Gravity.START
                            val half = (60 * den).toInt()
                            params.x = displayWidth / 2 - half
                            params.y = 0
                            windowManager.updateViewLayout(overlayView, params)
                            randomQuoteHandler.removeCallbacksAndMessages(null)
                            isQuoteScheduled = false
                            showBubble("🎮 Mode game aktif — $appName")
                        }
                    } else if (!isGameModeApp && !isGame && GameModeManager.autoGameMode) {
                        val (restoreX, restoreY) = GameModeManager.exitGameMode()
                        handler.post {
                            params.x = restoreX
                            params.y = restoreY
                            windowManager.updateViewLayout(overlayView, params)
                            showBubble("Mode game dinonaktifkan")
                        }
                    }
                }

            }

            if (screenOn && System.currentTimeMillis() - lastCommentTime > 60_000) {
                lastCommentTime = System.currentTimeMillis()
                val appName = GameModeManager.currentAppName ?: lastDetectedApp
                if (appName != null) {
                    val isGame = GameModeManager.isGameMode || (lastDetectedApp?.let { GameModeManager.isGame(this@OverlayService, it) } ?: false)
                    generateContextComment(appName, isGame)
                }
            }

            delay(3000)
        }
    }

    private val gameFallbackComments = listOf(
        "Seru juga mainnya, tapi masih kalah sama latihanku~",
        "Hmph, bagus sih, tapi jangan lupa latihan juga!",
        "Fufu, kamu cukup mahir juga ternyata.",
        "Mainnya oke, tapi jangan keseringan ya.",
        "... Lumayan. Tapi jangan lupa istirahat.",
        "Gamenya menarik? Ceritain dong~",
    )

    private val appFallbackComments = listOf(
        "Aplikasi itu, ya? Hmm, nggak terlalu menarik sih…",
        "Lagi sibuk ya? Baiklah, aku di sini aja.",
        "Fufu, kamu sibuk sekali hari ini.",
        "... Ada yang bisa aku bantu?",
        "Hmm, kamu betah juga di aplikasi itu.",
    )

    private fun generateContextComment(appName: String, isGame: Boolean) {
        activityScope.launch {
            val comment = LlmClient.generateActivityComment(appName, isGame)
            if (comment != null) {
                showBubble(comment)
            } else {
                val fallback = if (isGame) {
                    gameFallbackComments.random()
                } else {
                    appFallbackComments.random()
                }
                showBubble(fallback)
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

            bubbleText.visibility = View.GONE
            bubbleText.translationY = 0f
            bubbleText.text = text
            bubbleText.visibility = View.VISIBLE

            // position bubble above/below image based on screen placement
            bubbleText.post( Runnable {
                if (bubbleText.visibility != View.VISIBLE) return@Runnable
                val density = resources.displayMetrics.density
                val gap = (4 * density).toInt()
                val imageH = waifuHeight
                val bubbleH = bubbleText.height
                val centerY = params.y + imageH / 2
                val placeAbove = centerY > displayHeight / 2
                bubbleText.translationY = if (placeAbove) {
                    -(bubbleH + gap).toFloat()
                } else {
                    (imageH + gap).toFloat()
                }
            })

            playPopSound()

            val duration = (2000L + text.length * 50L).coerceAtMost(8000L)

            bubbleHideRunnable?.let { handler.removeCallbacks(it) }

            bubbleHideRunnable = Runnable {
                bubbleText.translationY = 0f
                bubbleText.visibility = View.GONE
            }

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

        activityScope.cancel()

        popPlayer?.release()
        popPlayer = null

        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
