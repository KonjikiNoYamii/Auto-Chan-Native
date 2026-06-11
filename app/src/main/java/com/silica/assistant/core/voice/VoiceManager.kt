package com.silica.assistant.core.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

object VoiceManager {

    private const val TAG = "VoiceManager"

    private var appContext: Context? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var componentName: ComponentName? = null

    private var isListening = false
    private var isDestroyed = false
    private var stoppedByResult = false

    var onResult: ((String) -> Unit)? = null
    var onStateChange: ((Boolean) -> Unit)? = null

    var onReadyForSpeech: (() -> Unit)? = null
    var onBeginningOfSpeech: (() -> Unit)? = null
    var onEndOfSpeech: (() -> Unit)? = null
    var onErrorCallback: ((Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    private var lastRealSpeechTime = 0L
    private var resultCount = 0
    private var isRestarting = false

    // Debounce auto-restart to avoid rapid on-off loops
    private var lastAutoRestartTime = 0L
    private val minAutoRestartInterval = 3000L

    private val watchdog =
            object : Runnable {
                override fun run() {
                    if (isDestroyed) return
                    if (isListening) {
                        val now = System.currentTimeMillis()
                        if (now - lastRealSpeechTime > 15000) {
                            Log.w(TAG, "Watchdog triggered restart (no real speech for 15s)")
                            resultCount = 0
                            restartRecognizer()
                        }
                    }
                    handler.postDelayed(this, 8000)
                }
            }

    fun init(context: Context, component: ComponentName? = null) {
        if (speechRecognizer != null) return
        appContext = context.applicationContext
        componentName = component
        createRecognizer()
        handler.post(watchdog)
        Log.d(TAG, "VoiceManager initialized")
    }

    private fun createRecognizer() {
        val ctx = appContext ?: return
        speechRecognizer = if (componentName != null) {
            SpeechRecognizer.createSpeechRecognizer(ctx, componentName)
                ?: SpeechRecognizer.createSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }

        speechIntent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

        speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        onReadyForSpeech?.invoke()
                    }

                    override fun onBeginningOfSpeech() {
                        lastRealSpeechTime = System.currentTimeMillis()
                        Log.d(TAG, "Speech started")
                        onBeginningOfSpeech?.invoke()
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                        onEndOfSpeech?.invoke()
                    }

                    override fun onError(error: Int) {
                        Log.e(TAG, "Error: $error")

                        isListening = false
                        onStateChange?.invoke(false)
                        if (!stoppedByResult) {
                            onErrorCallback?.invoke(error)
                        }
                        stoppedByResult = false
                        lastRealSpeechTime = System.currentTimeMillis()

                        restartRecognizer()

                        // Auto-restart only for transient errors, with debounce
                        val shouldAutoStart = error in listOf(
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,    // 1
                            SpeechRecognizer.ERROR_NETWORK,            // 2
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,    // 8
                        )
                        // ERROR_AUDIO (3), ERROR_SERVER (4), ERROR_CLIENT (5),
                        // ERROR_SPEECH_TIMEOUT (6), ERROR_NO_MATCH (7),
                        // ERROR_INSUFFICIENT_PERMISSIONS (9) — don't auto-start

                        if (shouldAutoStart) {
                            val now = System.currentTimeMillis()
                            if (now - lastAutoRestartTime > minAutoRestartInterval) {
                                lastAutoRestartTime = now
                                handler.postDelayed({
                                    if (!isDestroyed) {
                                        start()
                                    }
                                }, 500)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        lastRealSpeechTime = System.currentTimeMillis()

                        val text =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()

                        if (text != null) {
                            stoppedByResult = true
                            onResult?.invoke(text)
                        }

                        stop()

                        resultCount++
                        if (resultCount >= 5) {
                            resultCount = 0
                            restartRecognizer()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
        )
    }

    // =========================
    // PUBLIC API
    // =========================
    fun start() {
        if (isListening) return
        stoppedByResult = false
        if (speechRecognizer == null) {
            createRecognizer()
            handler.postDelayed({
                if (!isDestroyed) start()
            }, 500) // Increased delay for stability
            return
        }
        try {
            isListening = true
            lastRealSpeechTime = System.currentTimeMillis()
            onStateChange?.invoke(true)
            // Ensure any previous session is fully cleared
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(speechIntent)
            Log.d(TAG, "startListening() called")
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            isListening = false
            onStateChange?.invoke(false)
            restartRecognizer()
        }
    }

    fun stop() {
        if (!isListening) return
        isListening = false
        onStateChange?.invoke(false)
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "stop error", e)
        }
        Log.d(TAG, "stopped")
    }

    // =========================
    // RECOVERY
    // =========================
    private fun restartRecognizer() {
        if (isRestarting) return
        isRestarting = true
        try {
            Log.w(TAG, "Restarting SpeechRecognizer...")
            isListening = false
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            createRecognizer()
        } catch (e: Exception) {
            Log.e(TAG, "restart failed", e)
        } finally {
            isRestarting = false
        }
    }

    // =========================
    // CLEANUP
    // =========================
    fun destroy() {
        isDestroyed = true
        handler.removeCallbacks(watchdog)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
        Log.d(TAG, "VoiceManager destroyed")
    }
}
