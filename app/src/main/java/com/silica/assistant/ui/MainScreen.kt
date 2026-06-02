package com.silica.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silica.assistant.R
import com.silica.assistant.core.CommandManager
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import com.silica.assistant.ui.components.*
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.theme.GlassRose
import com.silica.assistant.ui.theme.GlassWhite
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.viewmodel.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    val greeting = remember {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Selamat pagi"
            hour < 15 -> "Selamat siang"
            hour < 18 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeaderSection(greeting = greeting)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QuickActionChips()

                Spacer(modifier = Modifier.height(16.dp))

                StatusBar()

                Spacer(modifier = Modifier.height(16.dp))

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
}

@Composable
private fun HeaderSection(greeting: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x80000000),
                            Color(0x00000000),
                            Color(0xFFF2EAE1)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.mybinik),
                contentDescription = "Waifu",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Espresso
                )
                Text(
                    text = "Apa yang bisa saya bantu?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8C7A70)
                )
            }
        }
    }
}

@Composable
private fun QuickActionChips() {
    val chips = listOf(
        ChipData("Terminal", Icons.Filled.Terminal, DeepRose),
        ChipData("File", Icons.Filled.Folder, DeepRose),
        ChipData("SSH", Icons.Filled.Lan, DeepRose),
        ChipData("Screenshot", Icons.Filled.Screenshot, DeepRose),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        chips.forEach { chip ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                FilledIconButton(
                    onClick = { },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = chip.color.copy(alpha = 0.15f),
                        contentColor = chip.color
                    )
                ) {
                    Icon(chip.icon, contentDescription = chip.label, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun StatusBar() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusItem(Icons.Filled.Wifi, "WiFi")
            StatusItem(Icons.Filled.Computer, "Laptop: offline")
            StatusItem(Icons.Filled.Storage, "SSH: --")
        }
    }
}

@Composable
private fun StatusItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ChipData(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)
