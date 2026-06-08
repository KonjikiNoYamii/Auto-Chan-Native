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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
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
import com.silica.assistant.core.screen.ScreenCaptureManager
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
import kotlinx.coroutines.withContext
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
    private var nextCommentDelay = Random.nextLong(20_000, 60_000)
    private var lastAutoScreenCommentTime = 0L
    private val autoScreenCommentInterval = 120_000L
    private var lastGameTouchTime = 0L
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

                        // game mode transparency: opaque on touch, fade after 10s idle
                    if (GameModeManager.isGameMode) {
                        val idleFromTouch = System.currentTimeMillis() - lastGameTouchTime
                        val targetAlpha = if (idleFromTouch < 10_000 || bubbleText.visibility == View.VISIBLE) 1.0f else 0.55f
                        if (waifuImage.alpha != targetAlpha) waifuImage.alpha = targetAlpha
                    }

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

        // Instant check for Game Mode on start
        activityScope.launch {
            delay(1000) // Give accessibility service a moment to connect
            val acc = OverlayEventBus.accessibilityService
            val pkg = acc?.rootInActiveWindow?.packageName?.toString()
            if (pkg != null) {
                android.util.Log.d("GameModeDebug", "Initial check: $pkg")
                if (GameModeManager.isGame(this@OverlayService, pkg)) {
                    val den = if (waifuWidth > 0) waifuWidth / 120f else 2f
                    GameModeManager.enterGameMode(this@OverlayService, params.x, params.y, displayWidth, displayHeight, den, auto = true)
                    handler.post {
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = displayWidth / 2 - (60 * den).toInt()
                        params.y = 0
                        windowManager.updateViewLayout(overlayView, params)
                    }
                }
            }
        }

        VoiceManager.onResult = { text ->
            VoiceManager.stop()
            showBubble("🎤 $text")
            handler.postDelayed({
                if (bubbleText.text == "🎤 $text") {
                    showBubble("...")
                }
            }, 800)
            CommandManager.execute(this, text)
        }

        VoiceManager.onStateChange = { listening ->
            if (!GameModeManager.isGameMode) {
                WaifuStateManager.currentState =
                        if (listening) WaifuState.LISTEN else WaifuState.RELAX
            }
        }

        VoiceManager.onErrorCallback = { error ->
            val msg = when (error) {
                7 -> "Hmph, aku nggak denger apa-apa..." // NO_MATCH
                6 -> "Kok diem aja? Capek ya?" // SPEECH_TIMEOUT
                5 -> "Duh, sistem suaranya lagi sibuk, coba bentar lagi ya~" // CLIENT
                1, 2 -> "Aduh, koneksinya lagi ampas nih..." // NETWORK
                else -> "Ada error dikit ($error), coba lagi nanti ya~"
            }
            showBubble(msg)
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

        ScreenCaptureManager.init(this)
        ScreenCaptureManager.tryRestore(this)

        OverlayEventBus.screenCaptureCallback = {
            handleScreenInfo()
        }

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

        if (OverlayEventBus.accessibilityService == null) {
            handler.postDelayed({
                showBubble("Aktifkan aksesibilitas Silica di Settings > Aksesibilitas")
            }, 8000)
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
                    lastGameTouchTime = System.currentTimeMillis()
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
            try {
                val granted = activityDetector.isUsageStatsGranted()
                if (granted && !detecting) {
                    detecting = true
                    handler.post { showBubble("✅ Mode game siap") }
                } else if (!granted && detecting) {
                    detecting = false
                    handler.post { showBubble("🔒 Akses penggunaan aplikasi belum diizinkan") }
                }

                if (!detecting) {
                    delay(3000)
                    continue
                }

                // Use Accessibility Service for more reliable foreground detection
                val acc = OverlayEventBus.accessibilityService
                val pkg = acc?.rootInActiveWindow?.packageName?.toString()
                android.util.Log.d("GameModeDebug", "Foreground app (via Acc): $pkg")
                
                if (pkg != null && pkg != packageName) {
                    val appName = GameModeManager.getAppName(this, pkg)
                    val isGame = GameModeManager.isGame(this, pkg)
                    
                    android.util.Log.d("GameModeDebug", "Processing $pkg, isGame=$isGame, isGameMode=${GameModeManager.isGameMode}")
                    
                    GameModeManager.currentAppPackage = pkg
                    GameModeManager.currentAppName = appName

                    if (!GameModeManager.manualMode) {
                        val isGameModeApp = pkg == GameModeManager.gameModeAppPackage
                        // Relax condition: trigger if it's a game AND not already in game mode
                        if ((isGameModeApp || isGame) && !GameModeManager.isGameMode) {
                            android.util.Log.d("GameModeDebug", "Entering Game Mode for $pkg")
                            val den = if (waifuWidth > 0) waifuWidth / 120f else 2f
                            GameModeManager.enterGameMode(this, params.x, params.y, displayWidth, displayHeight, den, auto = true)
                            generateContextComment(appName, true)
                            handler.post {
                                params.gravity = Gravity.TOP or Gravity.START
                                params.x = displayWidth / 2 - (60 * den).toInt()
                                params.y = 0
                                windowManager.updateViewLayout(overlayView, params)
                            }
                        } else if (!isGameModeApp && !isGame && GameModeManager.autoGameMode && GameModeManager.isGameMode) {
                            android.util.Log.d("GameModeDebug", "Exiting Game Mode")
                            GameModeManager.exitGameMode()
                            handler.post { showBubble("Mode game dinonaktifkan") }
                        }
                    }
                    lastDetectedApp = pkg
                }

                // Periodic comment logic (only if screen on and in game mode)
                if (screenOn && GameModeManager.isGameMode) {
                    val elapsed = System.currentTimeMillis() - lastCommentTime
                    if (elapsed > nextCommentDelay) {
                        lastCommentTime = System.currentTimeMillis()
                        nextCommentDelay = Random.nextLong(20_000, 90_000)
                        generateContextComment(GameModeManager.currentAppName ?: "Game", true)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GameModeDebug", "Error in loop: ${e.message}")
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

    private fun handleScreenInfo() {
        // Prevent screen capture during Game Mode to save resources and avoid interference
        if (GameModeManager.isGameMode) {
            android.util.Log.d("OverlayService", "Screen info skipped: Game Mode active")
            return
        }

        activityScope.launch {
            val acc = OverlayEventBus.accessibilityService
            val uiText = withContext(Dispatchers.Main) {
                acc?.getScreenText() ?: ""
            }
            val appName = lastDetectedApp ?: "unknown"
            
            // Re-check readiness inside the launch scope
            val ready = ScreenCaptureManager.isReady()
            
            if (acc == null) {
                showBubble("Aktifkan aksesibilitas Silica dulu di Settings > Aksesibilitas ya~")
                return@launch
            }
            
            if (!ready) {
                showBubble("Aku butuh izin buat liat layar kamu bentar. Klik 'Start Now' di popup nanti ya~")
                withContext(Dispatchers.Main) {
                    handler.postDelayed({
                        OverlayEventBus.navigateScreen.value = "request_screen_capture"
                    }, 1500)
                }
                return@launch
            }
            
            val screenshotJpeg = ScreenCaptureManager.captureScaledJpeg(800)

            val comment = LlmClient.describeScreen(appName, uiText, screenshotJpeg)
            if (comment != null) {
                showBubble(comment)
            } else {
                showBubble("Hmm, lagi nggak ada yang menarik sih.")
            }
        }
    }

    private fun generateAutoScreenComment() {
        if (!screenOn) return
        val now = System.currentTimeMillis()
        if (now - lastAutoScreenCommentTime < autoScreenCommentInterval) return
        lastAutoScreenCommentTime = now

        activityScope.launch {
            val appName = lastDetectedApp?.let {
                GameModeManager.getAppName(this@OverlayService, it)
            } ?: return@launch

            val uiText = withContext(Dispatchers.Main) {
                OverlayEventBus.accessibilityService?.getScreenText() ?: ""
            }

            val comment = LlmClient.generateScreenComment(appName, uiText)
            if (comment != null) {
                showBubble(comment)
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

    private var bubbleTypingRunnable: Runnable? = null

    fun showBubble(text: String) {

        handler.post {

            if (!::bubbleText.isInitialized) return@post

            bubbleTypingRunnable?.let { handler.removeCallbacks(it) }
            bubbleHideRunnable?.let { handler.removeCallbacks(it) }

            bubbleText.text = ""
            bubbleText.visibility = View.VISIBLE
            bubbleText.alpha = 0f
            bubbleText.scaleX = 0.8f
            bubbleText.scaleY = 0.8f
            
            bubbleText.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()

            lastGameTouchTime = System.currentTimeMillis()

            val density = resources.displayMetrics.density
            val onRightSide = params.x > displayWidth / 2
            val availableRight = (displayWidth - params.x - (8 * density).toInt()).coerceAtLeast((120 * density).toInt())
            val maxW = availableRight.coerceAtMost((240 * density).toInt())
            bubbleText.maxWidth = maxW
            bubbleText.gravity = if (onRightSide) Gravity.END else Gravity.START

            playPopSound()

            // Typing effect
            var charIndex = 0
            bubbleTypingRunnable = object : Runnable {
                override fun run() {
                    if (charIndex <= text.length) {
                        bubbleText.text = text.substring(0, charIndex)
                        charIndex++
                        val delay = if (charIndex < text.length && text[charIndex-1] in listOf('.', '!', '?', ',')) 200L else 30L
                        handler.postDelayed(this, delay)
                    } else {
                        val duration = (2000L + text.length * 30L).coerceAtMost(8000L)
                        bubbleHideRunnable = Runnable {
                            bubbleText.animate()
                                .alpha(0f)
                                .scaleX(0.8f)
                                .scaleY(0.8f)
                                .setDuration(200)
                                .withEndAction { bubbleText.visibility = View.GONE }
                                .start()
                        }
                        handler.postDelayed(bubbleHideRunnable!!, duration)
                    }
                }
            }
            handler.post(bubbleTypingRunnable!!)
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

        OverlayEventBus.screenCaptureCallback = null

        activityScope.cancel()

        popPlayer?.release()
        popPlayer = null

        windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
