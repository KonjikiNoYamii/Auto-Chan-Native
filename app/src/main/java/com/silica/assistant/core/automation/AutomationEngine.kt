package com.silica.assistant.core.automation

import android.content.Context
import android.os.Handler
import android.util.Log
import com.silica.assistant.core.debug.CommentDebugEntry
import com.silica.assistant.core.debug.CommentDebugger
import com.silica.assistant.core.debug.DebugTier
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.screen.ScreenCaptureManager
import com.silica.assistant.overlay.GameModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AutomationEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handler: Handler,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun showBubble(text: String, persistent: Boolean)
        fun setBubbleText(text: String)
        fun finalizeBubble(text: String)
        fun navigateTo(screen: String)
        fun getLastDetectedApp(): String?
    }

    private var isCommentPending = false

    fun normalizeContextHint(hint: String?): String? {
        if (hint.isNullOrBlank()) return hint
        var result = hint.lowercase()

        result = result.replace(Regex("\\bsaya\\b"), "kamu")
        result = result.replace(Regex("\\baku\\b"), "kamu")

        if (result.endsWith("ku")) {
            result = result.substring(0, result.length - 2) + "mu"
        }

        result = result.replace("diriku", "dirimu")
        result = result.replace("milikku", "milikmu")

        return result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun generateGameComment(appName: String, screenText: String, contextHint: String? = null) {
        if (isCommentPending) return
        isCommentPending = true
        val normalized = normalizeContextHint(contextHint)
        if (normalized.isNullOrBlank()) {
            callbacks.showBubble("Mari kita lihat...", persistent = true)
        } else {
            callbacks.showBubble("$normalized? Sebentar...", persistent = true)
        }
        val startTime = System.currentTimeMillis()
        scope.launch {
            try {
                // Tier 1: Screenshot + Gemini vision (pure AI) with streaming
                val startT1 = System.currentTimeMillis()
                val tokenBuf1 = StringBuilder()
                val visionResult = withTimeoutOrNull(30_000L) {
                    if (ScreenCaptureManager.isReady() && LlmClient.activeProvider == "Gemini") {
                        val screenshot = ScreenCaptureManager.captureScaledJpeg(800)
                        if (screenshot != null) {
                            LlmClient.describeScreen(appName, screenText, screenshot, contextHint, onToken = { t ->
                                tokenBuf1.append(t)
                                callbacks.setBubbleText(tokenBuf1.toString())
                            })
                        } else null
                    } else null
                }
                if (visionResult != null) {
                    callbacks.finalizeBubble(visionResult)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = contextHint,
                        promptSent = contextHint ?: "(periodik)", response = visionResult,
                        tier = DebugTier.VISION, durationMs = System.currentTimeMillis() - startT1,
                        screenshotUsed = true, provider = LlmClient.activeProvider))
                    return@launch
                }

                // Tier 2: Text-based LLM (pure AI from accessibility text)
                if (screenText.length > 10) {
                    val startT2 = System.currentTimeMillis()
                    val tokenBuf2 = StringBuilder()
                    val screenComment = LlmClient.generateScreenComment(appName, screenText, contextHint, onToken = { t ->
                        tokenBuf2.append(t)
                        callbacks.setBubbleText(tokenBuf2.toString())
                    })
                    if (screenComment != null) {
                        callbacks.finalizeBubble(screenComment)
                        CommentDebugger.record(CommentDebugEntry(
                            appName = appName, contextHint = contextHint,
                            promptSent = "screen_text: ${screenText.take(100)}", response = screenComment,
                            tier = DebugTier.TEXT_AI, durationMs = System.currentTimeMillis() - startT2,
                            provider = LlmClient.activeProvider))
                        return@launch
                    }
                }

                // Tier 3: App-name LLM fallback (pure AI)
                val startT3 = System.currentTimeMillis()
                val tokenBuf3 = StringBuilder()
                val appComment = LlmClient.generateActivityComment(appName, true, contextHint, onToken = { t ->
                    tokenBuf3.append(t)
                    callbacks.setBubbleText(tokenBuf3.toString())
                })
                if (appComment != null) {
                    callbacks.finalizeBubble(appComment)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = contextHint,
                        promptSent = "app_name: $appName", response = appComment,
                        tier = DebugTier.APP_AI, durationMs = System.currentTimeMillis() - startT3,
                        provider = LlmClient.activeProvider))
                } else {
                    callbacks.showBubble("Hmm, lagi bingung lihatnya. Coba lain kali ya~", false)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = contextHint,
                        promptSent = "app_name: $appName", response = null,
                        tier = DebugTier.ERROR, durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = "Semua tier gagal", provider = LlmClient.activeProvider))
                }
            } finally {
                isCommentPending = false
            }
        }
    }

    fun handleScreenInfo() {
        if (isCommentPending) return
        if (GameModeManager.isGameMode) {
            Log.d("AutomationEngine", "Screen info skipped: Game Mode active")
            return
        }

        isCommentPending = true
        callbacks.showBubble("Mari kita lihat...", persistent = true)
        scope.launch {
            try {
                val acc = OverlayEventBus.accessibilityService
                val uiText = withContext(Dispatchers.Main) {
                    acc?.getScreenText() ?: ""
                }
                val appName = callbacks.getLastDetectedApp() ?: "unknown"

                val ready = ScreenCaptureManager.isReady()

                if (acc == null) {
                    callbacks.showBubble("Aktifkan aksesibilitas Silica dulu ya~", false)
                    withContext(Dispatchers.Main) {
                        callbacks.navigateTo("accessibility_settings")
                    }
                    return@launch
                }

                if (!ready) {
                    callbacks.showBubble("Aku butuh izin buat liat layar kamu bentar. Klik 'Start Now' di popup nanti ya~", false)
                    withContext(Dispatchers.Main) {
                        handler.postDelayed({
                            callbacks.navigateTo("request_screen_capture")
                        }, 1500)
                    }
                    return@launch
                }

                val screenshotJpeg = ScreenCaptureManager.captureScaledJpeg(800)
                val startTime = System.currentTimeMillis()

                try {
                    val tokenBuf = StringBuilder()
                    val comment = LlmClient.describeScreen(appName, uiText, screenshotJpeg, onToken = { t ->
                        tokenBuf.append(t)
                        callbacks.setBubbleText(tokenBuf.toString())
                    })
                    if (comment != null) {
                        callbacks.finalizeBubble(comment)
                        CommentDebugger.record(CommentDebugEntry(
                            appName = appName, contextHint = "Manual Scan",
                            promptSent = "screen_info request", response = comment,
                            tier = if (screenshotJpeg != null) DebugTier.VISION else DebugTier.TEXT_AI,
                            durationMs = System.currentTimeMillis() - startTime,
                            screenshotUsed = screenshotJpeg != null,
                            provider = LlmClient.activeProvider))
                    } else {
                        callbacks.showBubble("Hmm, lagi nggak ada yang menarik sih.", false)
                        CommentDebugger.record(CommentDebugEntry(
                            appName = appName, contextHint = "Manual Scan",
                            promptSent = "screen_info request", response = null,
                            tier = DebugTier.ERROR, durationMs = System.currentTimeMillis() - startTime,
                            errorMessage = "Describe screen null",
                            provider = LlmClient.activeProvider))
                    }
                } catch (e: Exception) {
                    callbacks.showBubble("Hmm, lagi nggak bisa lihat layar sekarang.", false)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = "Manual Scan",
                        promptSent = "screen_info request", response = null,
                        tier = DebugTier.ERROR, durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = e.message ?: "Unknown error",
                        provider = LlmClient.activeProvider))
                }
            } finally {
                isCommentPending = false
            }
        }
    }

    fun generateAutoScreenComment(lastAutoScreenCommentTime: Long, autoScreenCommentInterval: Long, screenOn: Boolean) {
        if (isCommentPending) return
        if (!screenOn) return
        val now = System.currentTimeMillis()
        if (now - lastAutoScreenCommentTime < autoScreenCommentInterval) return

        isCommentPending = true
        callbacks.showBubble("Hmm...", persistent = true)
        scope.launch {
            try {
                val appName = callbacks.getLastDetectedApp()?.let {
                    GameModeManager.getAppName(context, it)
                } ?: return@launch

                val uiText = withContext(Dispatchers.Main) {
                    OverlayEventBus.accessibilityService?.getScreenText() ?: ""
                }

                val startTime = System.currentTimeMillis()
                val tokenBuf = StringBuilder()
                val comment = LlmClient.generateScreenComment(appName, uiText, onToken = { t ->
                    tokenBuf.append(t)
                    callbacks.setBubbleText(tokenBuf.toString())
                })
                if (comment != null) {
                    callbacks.finalizeBubble(comment)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = "Auto Screen",
                        promptSent = "screen_text: ${uiText.take(100)}", response = comment,
                        tier = DebugTier.TEXT_AI, durationMs = System.currentTimeMillis() - startTime,
                        provider = LlmClient.activeProvider))
                } else {
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = "Auto Screen",
                        promptSent = "screen_text: ${uiText.take(100)}", response = null,
                        tier = DebugTier.ERROR, durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = "Auto screen comment null",
                        provider = LlmClient.activeProvider))
                }
            } finally {
                isCommentPending = false
            }
        }
    }

    fun generateContextComment(appName: String, isGame: Boolean) {
        if (isCommentPending) return
        isCommentPending = true
        callbacks.showBubble("Mari kita lihat...", persistent = true)
        val startTime = System.currentTimeMillis()
        val tokenBuf = StringBuilder()
        scope.launch {
            try {
                val comment = LlmClient.generateActivityComment(appName, isGame, onToken = { t ->
                    tokenBuf.append(t)
                    callbacks.setBubbleText(tokenBuf.toString())
                })
                if (comment != null) {
                    callbacks.finalizeBubble(comment)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = if (isGame) "Auto-Game" else "Auto-App",
                        promptSent = "app_name: $appName", response = comment,
                        tier = DebugTier.APP_AI, durationMs = System.currentTimeMillis() - startTime,
                        provider = LlmClient.activeProvider))
                } else {
                    callbacks.showBubble("Hmm, lagi bingung lihatnya. Coba lain kali ya~", false)
                    CommentDebugger.record(CommentDebugEntry(
                        appName = appName, contextHint = if (isGame) "Auto-Game" else "Auto-App",
                        promptSent = "app_name: $appName", response = null,
                        tier = DebugTier.ERROR, durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = "Activity comment null", provider = LlmClient.activeProvider))
                }
            } finally {
                isCommentPending = false
            }
        }
    }

    fun isPending(): Boolean = isCommentPending
}
