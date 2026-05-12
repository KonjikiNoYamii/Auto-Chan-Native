package com.silica.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.silica.assistant.core.CommandManager
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager

@Composable
fun MainScreen() {

    val context = LocalContext.current
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
    }

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    val speechRecognizer = remember {
        val component =
                android.content.ComponentName(
                        "com.google.android.googlequicksearchbox",
                        "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                )

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context, component)

        recognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {

                        Toast.makeText(context, "Listening...", Toast.LENGTH_SHORT).show()
                    }

                    override fun onBeginningOfSpeech() {

                        Toast.makeText(context, "Speech Started", Toast.LENGTH_SHORT).show()
                    }

                    override fun onResults(results: Bundle?) {

                        val matches =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                        val text = matches?.get(0) ?: ""

                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        WaifuStateManager.currentState = WaifuState.HAPPY

                        CommandManager.execute(context, text)

                        handler
                    }

                    override fun onEndOfSpeech() {

                        Toast.makeText(context, "Speech Ended", Toast.LENGTH_SHORT).show()
                    }

                    override fun onError(error: Int) {

                        Toast.makeText(context, "Speech Error: $error", Toast.LENGTH_LONG).show()
                        WaifuStateManager.currentState = WaifuState.IDLE
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
        )

        recognizer
    }
    DisposableEffect(Unit) { onDispose { speechRecognizer.destroy() } }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("AI Assistant Online")

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = { CommandManager.execute(context, "tolong buka spotify") }) {
            Text("Open Spotify")
        }
        Button(onClick = { CommandManager.execute(context, "buka youtube dong") }) {
            Text("Open Youtube")
        }
        Button(onClick = { CommandManager.execute(context, "open_browser") }) {
            Text("Open Browser")
        }
        Button(onClick = { CommandManager.execute(context, "open_settings") }) {
            Text("Open Settings")
        }
        Button(onClick = { CommandManager.execute(context, "start_overlay") }) {
            Text("Start Waifu Overlay")
        }
        Button(
                onClick = {
                    Toast.makeText(context, "Voice button clicked", Toast.LENGTH_SHORT).show()

                    if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                    ) {

                        Toast.makeText(context, "Starting listening...", Toast.LENGTH_SHORT).show()

                        try {
                            Toast.makeText(context, speechRecognizer.toString(), Toast.LENGTH_SHORT)
                                    .show()
                            WaifuStateManager.currentState = WaifuState.LISTENING

                            speechRecognizer.startListening(speechIntent)

                            Toast.makeText(context, "startListening called", Toast.LENGTH_SHORT)
                                    .show()
                        } catch (e: Exception) {

                            Toast.makeText(context, "Exception: ${e.message}", Toast.LENGTH_LONG)
                                    .show()
                        }
                    }
                }
        ) { Text("Voice Command") }
    }
}
