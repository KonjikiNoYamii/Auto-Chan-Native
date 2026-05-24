package com.silica.assistant.service

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silica.assistant.R
import com.silica.assistant.core.CommandManager

class VoiceForegroundService : Service() {

    private val TAG = "VoiceFGService"

    private var recognizer: SpeechRecognizer? = null
    private var intent: Intent? = null

    private var isListening = false

    private val handler = Handler(Looper.getMainLooper())

    private val restartLoop =
            object : Runnable {
                override fun run() {
                    if (!isListening) {
                        startListening()
                    }
                    handler.postDelayed(this, 5000)
                }
            }

    override fun onCreate() {
        super.onCreate()

        startForeground(1, createNotification())

        initRecognizer()

        handler.post(restartLoop)

        Log.d(TAG, "Voice service started")
    }

    // =========================
    // INIT
    // =========================
    private fun initRecognizer() {

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

        recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.e(TAG, "error: $error")
                        isListening = false
                        restartRecognizer()
                    }

                    override fun onResults(results: Bundle?) {

                        val text =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()

                        if (text != null) {
                            Log.d(TAG, "result: $text")
                            CommandManager.execute(this@VoiceForegroundService, text)
                        }

                        isListening = false
                        restartRecognizer()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
        )
    }

    // =========================
    // LISTEN LOOP
    // =========================
    private fun startListening() {

        if (isListening) return

        try {
            isListening = true
            recognizer?.startListening(intent)

            Log.d(TAG, "startListening")
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            restartRecognizer()
        }
    }

    private fun restartRecognizer() {

        try {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null

            initRecognizer()

            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "restart failed", e)
        }
    }

    // =========================
    // NOTIFICATION (WAJIB)
    // =========================
    private fun createNotification(): Notification {

        val channelId = "voice_channel"

        val channel =
                NotificationChannel(
                        channelId,
                        "Voice Assistant",
                        NotificationManager.IMPORTANCE_LOW
                )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
                .setContentTitle("Silica Assistant Active")
                .setContentText("Listening in background...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
    }

    // =========================
    // LIFECYCLE
    // =========================
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(restartLoop)

        recognizer?.destroy()
        recognizer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
