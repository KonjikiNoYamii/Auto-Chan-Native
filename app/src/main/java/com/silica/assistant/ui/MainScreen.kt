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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silica.assistant.core.CommandManager
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import com.silica.assistant.ui.components.*
import com.silica.assistant.ui.viewmodel.AssistantViewModel

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: AssistantViewModel = viewModel()

    val uiState = viewModel.uiState

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
                        viewModel.setListening(false)
                        val matches =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.get(0) ?: ""
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        WaifuStateManager.currentState = WaifuState.HAPPY
                        CommandManager.execute(context, text)
                        handler.postDelayed(
                                { WaifuStateManager.currentState = WaifuState.IDLE },
                                1000
                        )
                    }
                    override fun onEndOfSpeech() {
                        viewModel.setListening(false)
                        Toast.makeText(context, "Speech Ended", Toast.LENGTH_SHORT).show()
                    }
                    override fun onError(error: Int) {
                        viewModel.setListening(false)
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

    Scaffold { paddingValues ->
        Column(
                modifier =
                        Modifier.padding(paddingValues)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
        ) {
            CommandInputSection(
                    commandText = uiState.commandText,
                    onCommandChange = { viewModel.updateCommandText(it) },
                    onExecute = {
                        if (uiState.commandText.isNotBlank()) {
                            WaifuStateManager.currentState = WaifuState.HAPPY
                            CommandManager.execute(context, uiState.commandText)
                            handler.postDelayed(
                                    { WaifuStateManager.currentState = WaifuState.IDLE },
                                    1000
                            )
                            viewModel.clearCommand()
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            QuickActionsSection(onAction = { command -> CommandManager.execute(context, command) })

            Spacer(modifier = Modifier.height(16.dp))

            VoiceCommandSection(
                    isListening = uiState.isListening,
                    onStartListening = {
                        Toast.makeText(context, "Voice button clicked", Toast.LENGTH_SHORT).show()
                        if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            Toast.makeText(context, "Starting listening...", Toast.LENGTH_SHORT)
                                    .show()
                            try {
                                Toast.makeText(
                                                context,
                                                speechRecognizer.toString(),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                WaifuStateManager.currentState = WaifuState.LISTENING
                                viewModel.setListening(true)
                                speechRecognizer.startListening(speechIntent)
                                Toast.makeText(context, "startListening called", Toast.LENGTH_SHORT)
                                        .show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                                context,
                                                "Exception: ${e.message}",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CommandHistorySection()
        }
    }
}
