package com.silica.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silica.assistant.core.CommandManager
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import com.silica.assistant.ui.components.*
import com.silica.assistant.ui.theme.GlassRose
import com.silica.assistant.ui.theme.GlassWhite
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GlassOrb(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp),
            size = 200,
            color = GlassRose,
        )
        GlassOrb(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 60.dp),
            size = 160,
            color = GlassWhite,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

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

            VoiceCommandSection(
                isListening = uiState.isListening,
                onStartListening = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            WaifuStateManager.currentState = WaifuState.LISTENING
                            viewModel.setListening(true)
                            speechRecognizer.startListening(speechIntent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Exception: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CommandHistorySection()

            Spacer(modifier = Modifier.height(16.dp))

            OverlayControlSection()

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun GlassOrb(
    modifier: Modifier = Modifier,
    size: Int = 160,
    color: Color = GlassWhite,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.large)
            .background(color)
    )
}
