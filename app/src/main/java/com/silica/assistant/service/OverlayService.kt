package com.silica.assistant.service

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.silica.assistant.R
import com.silica.assistant.core.ActivityDetector
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.automation.AutomationEngine
import com.silica.assistant.core.debug.CommentDebugEntry
import com.silica.assistant.core.debug.CommentDebugger
import com.silica.assistant.core.debug.DebugTier
import com.silica.assistant.core.ssh.SshManager
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

import com.silica.assistant.core.llm.MoodManager
import org.koin.android.ext.android.inject

class OverlayService : Service() {

    private val moodManager: MoodManager by inject()
    private lateinit var automationEngine: AutomationEngine
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var waifuImage: ImageView
    private lateinit var bubbleText: TextView
    private lateinit var confirmLayout: View
    private lateinit var confirmYes: TextView
    private lateinit var confirmNo: TextView

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
    private var lastDetectedEvent: String? = null
    private var lastCommentTime = 0L
    private var nextCommentDelay = Random.nextLong(20_000, 60_000)
    private var lastAutoScreenCommentTime = 0L
    private val autoScreenCommentInterval = 120_000L
    private var lastGameTouchTime = 0L
    private var detecting = false
    private var nonGameCount = 0


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

                        // Real-time size update
                        val metrics_sz = resources.displayMetrics
                        val targetSizeDp = if (GameModeManager.isGameMode) 
                            com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode 
                        else 
                            com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault
                        val targetPx = (targetSizeDp * metrics_sz.density).toInt()
                        
                        if (waifuWidth != targetPx) {
                            waifuWidth = targetPx
                            waifuHeight = targetPx
                            waifuImage.layoutParams.width = waifuWidth
                            waifuImage.layoutParams.height = waifuHeight
                            windowManager.updateViewLayout(overlayView, params)
                        }

                        // game mode transparency: opaque on touch, fade after 10s idle
                    if (GameModeManager.isGameMode) {
                        val idleFromTouch = System.currentTimeMillis() - lastGameTouchTime
                        val targetAlpha = if (idleFromTouch < 10_000 || bubbleText.visibility == View.VISIBLE) 1.0f else 0.55f
                        if (waifuImage.alpha != targetAlpha) waifuImage.alpha = targetAlpha
                    }

                        val gameReq = OverlayEventBus.gameModeRequest
                        if (gameReq != null) {
                            OverlayEventBus.gameModeRequest = null
                            
                            val metrics = resources.displayMetrics
                            if (gameReq) {
                                val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode
                                waifuWidth = (sizeDp * metrics.density).toInt()
                                waifuHeight = (sizeDp * metrics.density).toInt()
                                waifuImage.layoutParams.width = waifuWidth
                                waifuImage.layoutParams.height = waifuHeight
                                
                                val den = waifuWidth / (120f * metrics.density) * metrics.density
                                GameModeManager.enterGameMode(this@OverlayService, params.x, params.y, displayWidth, displayHeight, den)
                                
                                val half = (60 * den).toInt()
                                params.gravity = Gravity.TOP or Gravity.START
                                params.x = displayWidth / 2 - half
                                params.y = 0
                                randomQuoteHandler.removeCallbacksAndMessages(null)
                                isQuoteScheduled = false
                                windowManager.updateViewLayout(overlayView, params)
                            } else {
                                val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault
                                waifuWidth = (sizeDp * metrics.density).toInt()
                                waifuHeight = (sizeDp * metrics.density).toInt()
                                waifuImage.layoutParams.width = waifuWidth
                                waifuImage.layoutParams.height = waifuHeight
                                
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or 
                           ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(2, createNotification(), type)
            } else {
                startForeground(2, createNotification())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        
        val sizeDp = if (GameModeManager.isGameMode) 
            com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode 
        else 
            com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault
            
        waifuWidth = (sizeDp * metrics.density).toInt()
        waifuHeight = (sizeDp * metrics.density).toInt()

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        waifuImage = overlayView.findViewById(R.id.waifuImage)
        
        // Apply initial size
        waifuImage.layoutParams.width = waifuWidth
        waifuImage.layoutParams.height = waifuHeight
        bubbleText = overlayView.findViewById(R.id.bubbleText)

        controller = WaifuExpressionController(waifuImage, this)

        confirmLayout = overlayView.findViewById(R.id.confirmLayout)
        confirmYes = overlayView.findViewById(R.id.confirmYes)
        confirmNo = overlayView.findViewById(R.id.confirmNo)

        confirmYes.text = "✓"
        confirmNo.text = "✗"
        confirmYes.visibility = View.GONE
        confirmNo.visibility = View.GONE
        confirmLayout.visibility = View.GONE

        handler.post(expressionUpdater)

        OverlayEventBus.onBubble = { text -> showBubble(text) }

        // 🧠 VOICE SYSTEM (ONLY ONE SOURCE)
        VoiceManager.init(this)
        val chatDao = org.koin.core.context.GlobalContext.get().get<com.silica.assistant.core.llm.db.ChatDao>()
        WaifuNotifier.init(this, chatDao)

        // Instant check for Game Mode on start
        activityScope.launch {
            delay(1000) // Give accessibility service a moment to connect
            val acc = OverlayEventBus.accessibilityService
            val pkg = acc?.rootInActiveWindow?.packageName?.toString()
            if (pkg != null) {
                android.util.Log.d("GameModeDebug", "Initial check: $pkg")
                if (GameModeManager.isGame(this@OverlayService, pkg)) {
                    val metrics = resources.displayMetrics
                    val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode
                    waifuWidth = (sizeDp * metrics.density).toInt()
                    waifuHeight = (sizeDp * metrics.density).toInt()
                    
                    val den = waifuWidth / (120f * metrics.density) * metrics.density
                    GameModeManager.enterGameMode(this@OverlayService, params.x, params.y, displayWidth, displayHeight, den, auto = true)
                    handler.post {
                        waifuImage.layoutParams.width = waifuWidth
                        waifuImage.layoutParams.height = waifuHeight
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
                    showBubble("... ( -_ -)")
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
            if (!(automationEngine.isPending() && error == 5)) {
                val msg = when (error) {
                    7 -> "Hmph, aku nggak denger apa-apa..." // NO_MATCH
                    6 -> "Kok diem aja? Capek ya?" // SPEECH_TIMEOUT
                    5 -> "Duh, sistem suaranya lagi sibuk, coba bentar lagi ya~" // CLIENT
                    1, 2 -> "Aduh, koneksinya lagi ampas nih..." // NETWORK
                    else -> "Ada error dikit ($error), coba lagi nanti ya~"
                }
                showBubble(msg)
                CommentDebugger.record(CommentDebugEntry(
                    appName = "VoiceSystem", contextHint = "Audio Input",
                    promptSent = "Error code: $error", response = null,
                    tier = DebugTier.ERROR, durationMs = 0,
                    errorMessage = msg, provider = "Android System"))
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

        ScreenCaptureManager.init(this)
        ScreenCaptureManager.tryRestore(this)

        automationEngine = AutomationEngine(
            context = this,
            scope = activityScope,
            handler = handler,
            callbacks = object : AutomationEngine.Callbacks {
                override fun showBubble(text: String, persistent: Boolean) {
                    this@OverlayService.showBubble(text, persistent)
                }
                override fun setBubbleText(text: String) {
                    this@OverlayService.setBubbleText(text)
                }
                override fun finalizeBubble(text: String) {
                    this@OverlayService.finalizeBubble(text)
                }
                override fun navigateTo(screen: String) {
                    OverlayEventBus.navigateScreen.value = screen
                }
                override fun getLastDetectedApp(): String? {
                    return this@OverlayService.lastDetectedApp
                }
            }
        )

        OverlayEventBus.screenCaptureCallback = {
            automationEngine.handleScreenInfo()
        }

        OverlayEventBus.gameCommentCallback = label@{ contextHint ->
            val appName = GameModeManager.currentAppName ?: lastDetectedApp ?: "Game"
            val screenText = OverlayEventBus.accessibilityService?.getScreenText() ?: ""
            if (GameModeManager.isGameMode) {
                if (!ScreenCaptureManager.isReady()) {
                    handler.post {
                        showBubble("Aku butuh izin buat liat layar kamu bentar. Klik 'Start Now' di popup nanti ya~")
                    }
                    handler.postDelayed({
                        OverlayEventBus.navigateScreen.value = "request_screen_capture"
                    }, 1500)
                    return@label
                }
                handler.post {
                    val normalized = automationEngine.normalizeContextHint(contextHint)
                    if (normalized.isNullOrBlank()) {
                        showBubble("Hmm, biarkan aku lihat...")
                    } else {
                        showBubble("$normalized? Biarkan aku lihat...")
                    }
                }
                automationEngine.generateGameComment(appName, screenText, contextHint)
            } else {
                automationEngine.generateContextComment(appName, false)
            }
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
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                    )
                } catch (_: Exception) {}
            }, 8000)
        }
    }

    private fun isIdle(): Boolean {
        if (!screenOn) return false
        val idleTime = System.currentTimeMillis() - lastTouchTime
        return idleTime > 30_000
    }

    private fun normalizeContextHint(hint: String?): String? {
        return automationEngine.normalizeContextHint(hint)
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

        activityScope.launch {
            // Priority 1: Proactive Reminders (Quests/Relationship)
            val reminder = moodManager.getProactiveReminder()
            if (reminder != null) {
                withContext(Dispatchers.Main) {
                    WaifuStateManager.currentState = WaifuState.TALK
                    showBubble(reminder)
                }
                return@launch
            }

            // Priority 2: Standard Random Quotes
            val (text, emotion) = YamiQuotes.random()
            withContext(Dispatchers.Main) {
                WaifuStateManager.currentState = when (emotion) {
                    "happy", "blush" -> WaifuState.TALK
                    else -> WaifuState.RELAX
                }
                showBubble(text)
            }
        }

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

                    // Don't consume touch on confirmation buttons — let them handle clicks
                    var isButtonTouch = false
                    if (::confirmLayout.isInitialized && confirmLayout.visibility == View.VISIBLE) {
                        val btnHit = Rect()
                        confirmYes.getHitRect(btnHit)
                        if (btnHit.contains(event.x.toInt(), event.y.toInt())) isButtonTouch = true
                        if (!isButtonTouch) {
                            confirmNo.getHitRect(btnHit)
                            if (btnHit.contains(event.x.toInt(), event.y.toInt())) isButtonTouch = true
                        }
                    }

                    if (isButtonTouch) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        false
                    } else {
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

                    // Trigger Spontaneous AI Comment (Non-Hardcoded)
                    if (!GameModeManager.isGameMode) {
                        activityDetector.checkAndTriggerSpontaneousComment(appName)
                    }

                    if (!GameModeManager.manualMode) {
                        val isGameModeApp = pkg == GameModeManager.gameModeAppPackage
                        val isGameOrModeApp = isGameModeApp || isGame

                        if (isGameOrModeApp && !GameModeManager.isGameMode) {
                            nonGameCount = 0
                            android.util.Log.d("GameModeDebug", "Entering Game Mode for $pkg")

                            val metrics = resources.displayMetrics
                            val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode
                            waifuWidth = (sizeDp * metrics.density).toInt()
                            waifuHeight = (sizeDp * metrics.density).toInt()
                            val den = waifuWidth / (120f * metrics.density) * metrics.density

                            GameModeManager.enterGameMode(this, params.x, params.y, displayWidth, displayHeight, den, auto = true)
                            lastCommentTime = System.currentTimeMillis()
                            // Request screen capture permission if not yet granted
                            if (!ScreenCaptureManager.isReady()) {
                                handler.postDelayed({
                                    showBubble("Aku butuh izin buat liat layar kamu biar bisa komentar lebih seru~")
                                }, 1000)
                                handler.postDelayed({
                                    OverlayEventBus.navigateScreen.value = "request_screen_capture"
                                }, 3500)
                            }
                            automationEngine.generateContextComment(appName, true)
                            handler.post {
                                waifuImage.layoutParams.width = waifuWidth
                                waifuImage.layoutParams.height = waifuHeight
                                params.gravity = Gravity.TOP or Gravity.START
                                params.x = displayWidth / 2 - (60 * den).toInt()
                                params.y = 0
                                windowManager.updateViewLayout(overlayView, params)
                            }
                            // Show accessibility level after enough samples (~12dtk)
                            activityScope.launch {
                                delay(12000)
                                val level = GameModeManager.getAccessibilityLevel(pkg)
                                if (level != com.silica.assistant.overlay.GameAccessibilityLevel.UNKNOWN) {
                                    handler.post { showBubble("📊 Aksesibilitas game: $level") }
                                }
                            }
                        } else if (!isGameOrModeApp && GameModeManager.autoGameMode && GameModeManager.isGameMode) {
                            nonGameCount++
                            android.util.Log.d("GameModeDebug", "Non-game count: $nonGameCount")
                            if (nonGameCount >= 2) {
                                nonGameCount = 0
                                android.util.Log.d("GameModeDebug", "Exiting Game Mode")
                                GameModeManager.exitGameMode()

                                val metrics = resources.displayMetrics
                                val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault
                                waifuWidth = (sizeDp * metrics.density).toInt()
                                waifuHeight = (sizeDp * metrics.density).toInt()

                                handler.post { 
                                    waifuImage.layoutParams.width = waifuWidth
                                    waifuImage.layoutParams.height = waifuHeight
                                    showBubble("Mode game dinonaktifkan") 
                                }
                            }
                        }
 else if (isGameOrModeApp && GameModeManager.isGameMode) {
                            nonGameCount = 0
                        }
                    }
                    lastDetectedApp = pkg
                }

                // Periodic comment logic (only if screen on and in game mode)
                if (screenOn && GameModeManager.isGameMode) {
                    val elapsed = System.currentTimeMillis() - lastCommentTime
                    val rawScreenText = acc?.getScreenText() ?: ""
                    
                    // Event Detection (Proactive)
                    val eventKeywords = mapOf(
                        "Victory" to listOf("Victory", "Menang", "Win", "Victory!"),
                        "Defeat" to listOf("Defeat", "Kalah", "Lose", "Defeat!"),
                        "Level Up" to listOf("Level Up", "Level Berhasil"),
                        "MVP" to listOf("MVP"),
                        "Epic" to listOf("Savage", "Maniac", "Legendary", "Mega Kill")
                    )
                    
                    var detectedEvent: String? = null
                    for ((event, keys) in eventKeywords) {
                        if (keys.any { rawScreenText.contains(it, ignoreCase = true) }) {
                            detectedEvent = event
                            break
                        }
                    }
                    
                    if (detectedEvent != null && detectedEvent != lastDetectedEvent) {
                        lastDetectedEvent = detectedEvent
                        lastCommentTime = System.currentTimeMillis()
                        android.util.Log.d("GameModeDebug", "Triggering: Event AI ($detectedEvent)")
                        val appName = GameModeManager.currentAppName ?: "Game"
                        automationEngine.generateGameComment(appName, rawScreenText, contextHint = "Kejadian menarik: $detectedEvent")
                    } else if (elapsed > nextCommentDelay) {
                        lastCommentTime = System.currentTimeMillis()
                        nextCommentDelay = Random.nextLong(180_000, 300_000)
                        android.util.Log.d("GameModeDebug", "Triggering: periodic AI")
                        val appName = GameModeManager.currentAppName ?: "Game"
                        automationEngine.generateGameComment(appName, rawScreenText)
                    }
                    
                    // Reset detected event if text cleared
                    if (detectedEvent == null && lastDetectedEvent != null) {
                        lastDetectedEvent = null
                    }

                    // Record accessibility level for this game
                    GameModeManager.recordAccessibilitySample(
                        GameModeManager.currentAppPackage ?: "",
                        rawScreenText.isNotBlank()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("GameModeDebug", "Error in loop: ${e.message}")
            }
            delay(3000)
        }
    }

    // ── Game mode: pure AI only ──

    private fun executeAutomation(action: String) {
        when (action) {
            "GAME_MODE" -> {
                if (!GameModeManager.isGameMode) {
                    val metrics = resources.displayMetrics
                    val sizeDp = com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode
                    waifuWidth = (sizeDp * metrics.density).toInt()
                    waifuHeight = (sizeDp * metrics.density).toInt()
                    val den = waifuWidth / (120f * metrics.density) * metrics.density
                    GameModeManager.enterGameMode(this, params.x, params.y, displayWidth, displayHeight, den, auto = true)

                    handler.post {
                        waifuImage.layoutParams.width = waifuWidth
                        waifuImage.layoutParams.height = waifuHeight
                        windowManager.updateViewLayout(overlayView, params)
                    }
                }
            }
            "BRIGHTNESS_MAX" -> com.silica.assistant.core.system.BrightnessController.max(this)
            "BRIGHTNESS_MIN" -> com.silica.assistant.core.system.BrightnessController.min(this)
            "VOLUME_UP" -> com.silica.assistant.core.system.VolumeController.volumeUp(this)
            "VOLUME_MAX" -> com.silica.assistant.core.system.VolumeController.maxVolume(this)
        }
    }

    private fun generateContextComment(appName: String, isGame: Boolean) {
        automationEngine.generateContextComment(appName, isGame)
    }

    private fun generateGameComment(appName: String, screenText: String, contextHint: String? = null) {
        automationEngine.generateGameComment(appName, screenText, contextHint)
    }

    private fun handleScreenInfo() {
        automationEngine.handleScreenInfo()
    }

    private fun generateAutoScreenComment() {
        val now = System.currentTimeMillis()
        if (now - lastAutoScreenCommentTime < autoScreenCommentInterval) return
        lastAutoScreenCommentTime = now
        automationEngine.generateAutoScreenComment(lastAutoScreenCommentTime, autoScreenCommentInterval, screenOn)
    }

    // =========================
    // BUBBLE UI
    // =========================
    private fun playPopSound() {

        try {
            popPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            popPlayer = null

            val customPop = CustomAssetManager.getCustomPath(this, CustomAssetManager.AssetType.POP_SOUND)
            val newPlayer = if (customPop != null) {
                MediaPlayer().apply {
                    setDataSource(customPop)
                    prepare()
                }
            } else {
                MediaPlayer.create(this, R.raw.pop)
            }
            popPlayer = newPlayer
            newPlayer.setVolume(0.6f, 0.6f)
            newPlayer.start()
            newPlayer.setOnCompletionListener { 
                it.release() 
                if (popPlayer == it) popPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var bubbleTypingRunnable: Runnable? = null
    private var bubbleDotsRunnable: Runnable? = null

    fun setBubbleText(text: String) {
        randomQuoteHandler.removeCallbacksAndMessages(null)
        handler.post {
            if (!::bubbleText.isInitialized) return@post
            bubbleTypingRunnable?.let { handler.removeCallbacks(it) }
            bubbleDotsRunnable?.let { handler.removeCallbacks(it) }
            bubbleHideRunnable?.let { handler.removeCallbacks(it) }
            bubbleText.text = text
        }
    }

    fun finalizeBubble(text: String) {
        handler.post {
            if (!::bubbleText.isInitialized) return@post
            if (::confirmLayout.isInitialized) confirmLayout.visibility = View.GONE
            bubbleTypingRunnable?.let { handler.removeCallbacks(it) }
            bubbleDotsRunnable?.let { handler.removeCallbacks(it) }
            bubbleHideRunnable?.let { handler.removeCallbacks(it) }
            randomQuoteHandler.removeCallbacksAndMessages(null)
            bubbleText.visibility = View.VISIBLE
            bubbleText.alpha = 1f
            bubbleText.scaleX = 1f
            bubbleText.scaleY = 1f
            bubbleText.text = text
            val duration = if (GameModeManager.isGameMode) {
                (1500L + text.length * 20L).coerceAtMost(4000L)
            } else {
                (2000L + text.length * 30L).coerceAtMost(8000L)
            }
            bubbleHideRunnable = Runnable {
                bubbleText.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction {
                        bubbleText.visibility = View.GONE
                        scheduleRandomQuote()
                    }
                    .start()
            }
            handler.postDelayed(bubbleHideRunnable!!, duration)
        }
    }

    fun showBubble(text: String, persistent: Boolean = false) {

        handler.post {

            if (!::bubbleText.isInitialized) return@post
            if (::confirmLayout.isInitialized) confirmLayout.visibility = View.GONE

            bubbleTypingRunnable?.let { handler.removeCallbacks(it) }
            bubbleHideRunnable?.let { handler.removeCallbacks(it) }
            bubbleDotsRunnable?.let { handler.removeCallbacks(it) }
            randomQuoteHandler.removeCallbacksAndMessages(null)

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

            // Shake effect for emotional reactions
            val lowerText = text.lowercase()
            val emotionalKeywords = listOf("!", "wah", "aduh", "ah", "gagal", "menang", "kalah", "hebat")
            if (emotionalKeywords.any { lowerText.contains(it) } && ::controller.isInitialized) {
                controller.shake()
            }

            val density = resources.displayMetrics.density
            val onRightSide = params.x > displayWidth / 2
            val availableRight = (displayWidth - params.x - (8 * density).toInt()).coerceAtLeast((120 * density).toInt())
            val baseMaxW = if (GameModeManager.isGameMode) 180 else 240
            val maxW = availableRight.coerceAtMost((baseMaxW * density).toInt())
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
                        val typingDelay = if (GameModeManager.isGameMode) 15L else 30L
                        val delay = if (charIndex < text.length && text[charIndex-1] in listOf('.', '!', '?', ',')) {
                            if (GameModeManager.isGameMode) 100L else 200L
                        } else typingDelay
                        handler.postDelayed(this, delay)
                    } else {
                        if (persistent && text.endsWith("...")) {
                            val baseText = text.removeSuffix("...")
                            var dots = 3
                            bubbleDotsRunnable = object : Runnable {
                                override fun run() {
                                    dots = if (dots >= 3) 1 else dots + 1
                                    bubbleText.text = baseText + ".".repeat(dots)
                                    handler.postDelayed(this, 500)
                                }
                            }
                            handler.postDelayed(bubbleDotsRunnable!!, 500)
                        } else {
                            val duration = if (GameModeManager.isGameMode) {
                                (1500L + text.length * 20L).coerceAtMost(4000L)
                            } else {
                                (2000L + text.length * 30L).coerceAtMost(8000L)
                            }
                            bubbleHideRunnable = Runnable {
                                bubbleText.animate()
                                    .alpha(0f)
                                    .scaleX(0.8f)
                                    .scaleY(0.8f)
                                    .setDuration(200)
                                    .withEndAction {
                                        bubbleText.visibility = View.GONE
                                        scheduleRandomQuote()
                                    }
                                    .start()
                            }
                            handler.postDelayed(bubbleHideRunnable!!, duration)
                        }
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

        ScreenCaptureManager.release()
        OverlayEventBus.screenCaptureCallback = null

        activityScope.cancel()

        popPlayer?.release()
        popPlayer = null

        windowManager.removeView(overlayView)
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_channel"
        val channel = NotificationChannel(
            channelId,
            "Silica Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Silica is Active")
            .setContentText("Waifu is watching over you~")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
