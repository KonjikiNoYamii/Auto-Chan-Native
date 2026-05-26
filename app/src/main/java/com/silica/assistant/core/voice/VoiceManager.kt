package com.silica.assistant.core.voice

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

    private var isListening = false
    private var isDestroyed = false

    var onResult: ((String) -> Unit)? = null
    var onStateChange: ((Boolean) -> Unit)? = null

    // 🧠 watchdog untuk detect silent death
    private val handler = Handler(Looper.getMainLooper())

    private var lastRealSpeechTime = 0L
    private var resultCount = 0
    private var isRestarting = false

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

    fun init(context: Context) {

        if (speechRecognizer != null) return

        appContext = context.applicationContext

        createRecognizer()

        handler.post(watchdog)

        Log.d(TAG, "VoiceManager initialized")
    }

    // =========================
    // CORE RECOGNIZER
    // =========================
    private fun createRecognizer() {

        val ctx = appContext ?: return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)

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
                    }

                    override fun onBeginningOfSpeech() {
                        lastRealSpeechTime = System.currentTimeMillis()
                        Log.d(TAG, "Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                    }

                    override fun onError(error: Int) {

                        Log.e(TAG, "Error: $error")

                        isListening = false
                        onStateChange?.invoke(false)
                        lastRealSpeechTime = System.currentTimeMillis()

                        // 🔥 critical fix: always recover
                        resultCount = 0
                        restartRecognizer()
                    }

                    override fun onResults(results: Bundle?) {

                        lastRealSpeechTime = System.currentTimeMillis()

                        val text =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()

                        if (text != null) {
                            onResult?.invoke(text)
                        }

                        stop()

                        // restart periodik tiap 5 hasil sukses
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

        val recognizer =
                speechRecognizer
                        ?: run {
                            restartRecognizer()
                            return
                        }

        try {
            isListening = true
            lastRealSpeechTime = System.currentTimeMillis()

            onStateChange?.invoke(true)

            recognizer.startListening(speechIntent)

            Log.d(TAG, "startListening() called")
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
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
    // 🔥 RECOVERY SYSTEM
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
