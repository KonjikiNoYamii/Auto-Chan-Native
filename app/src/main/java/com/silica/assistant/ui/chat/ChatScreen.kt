package com.silica.assistant.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import com.silica.assistant.R
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.config.AssistantConfig
import com.silica.assistant.core.llm.ChatMessage
import com.silica.assistant.core.llm.EmotionMapper
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.llm.LlmConfig
import com.silica.assistant.core.llm.MemoryManager
import com.silica.assistant.ui.theme.DeepRose
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showMemories by remember { mutableStateOf(false) }
    var showModelInfo by remember { mutableStateOf(false) }

    val assistantName = AssistantConfig.assistantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val chatIcon = remember(context) { CustomAssetManager.loadImageBitmap(context, CustomAssetManager.AssetType.CHAT_ICON) }

    val messages = viewModel.messages
    val isLoading = viewModel.isLoading
    val error = viewModel.error

    LaunchedEffect(Unit) {
        viewModel.loadMemories(context)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chatIcon != null) {
                            Image(
                                painter = BitmapPainter(chatIcon),
                                contentDescription = assistantName,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.iconchat),
                                contentDescription = assistantName,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(assistantName, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showModelInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = { showMemories = true }) {
                        Icon(Icons.Filled.Psychology, contentDescription = "Memory")
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg, assistantName = assistantName)
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "$assistantName sedang mengetik...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (error != null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            error,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ketik pesan...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        viewModel.sendMessage(context, inputText)
                        inputText = ""
                    },
                    modifier = Modifier.size(50.dp),
                    enabled = inputText.isNotBlank() && !isLoading,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DeepRose
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showMemories) {
        MemoriesDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showMemories = false }
        )
    }

    if (showModelInfo) {
        ModelInfoDialog(onDismiss = { showModelInfo = false })
    }
}

@Composable
private fun EmptyChatState() {
    val apiReady = LlmConfig.apiKey.isNotBlank()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.QuestionAnswer,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DeepRose.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Tanyakan apa saja ke AI",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DeepRose
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Koneksi AI",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val provider = LlmClient.activeProvider
                val localActive = provider == "LocalGemini"
                val geminiActive = provider == "Gemini" && LlmConfig.useGeminiFallback
                Text(
                    if (localActive) "Local Server Aktif" 
                    else if (geminiActive) "Gemini Server Aktif" 
                    else if (apiReady) "OpenRouter - Fallback" 
                    else "API key belum diatur",
                    fontSize = 12.sp,
                    color = if (localActive) Color(0xFF00FF88) 
                    else if (geminiActive) Color(0xFFFF69B4) 
                    else if (apiReady) Color(0xFF00FF88) 
                    else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Model: ${LlmConfig.model}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (localActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Server: ${LlmConfig.localEndpoint.removePrefix("https://").take(30)}...",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                if (geminiActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Server: ${LlmConfig.geminiEndpoint.removePrefix("https://").take(30)}...",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Ingatan akan tersimpan otomatis",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ModelInfoDialog(onDismiss: () -> Unit) {
    var modelInput by remember { mutableStateOf(LlmConfig.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = DeepRose) },
        title = { Text("Info AI") },
        text = {
            Column {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Nama Model") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val provider = LlmClient.activeProvider
                val geminiActive = provider == "Gemini" && LlmConfig.useGeminiFallback
                InfoRow("Provider Aktif", if (geminiActive) "Gemini" else "OpenRouter")
                InfoRow("Endpoint", if (geminiActive) LlmConfig.geminiEndpoint else LlmConfig.endpoint)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (modelInput.isNotBlank()) {
                    LlmConfig.model = modelInput
                }
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoriesDialog(
    context: Context,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val memories = viewModel.memories

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Psychology, contentDescription = null, tint = DeepRose) },
        title = { Text("Ingatan AI") },
        text = {
            if (memories.isEmpty()) {
                Text(
                    "Belum ada ingatan.\n\nIngatan akan tersimpan otomatis saat kamu ngobrol.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(memories.withIndex().toList()) { (i, mem) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = mem, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.deleteMemory(context, i) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                MemoryManager.clearAll(context)
                viewModel.loadMemories(context)
            }) {
                Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun ChatBubble(message: ChatMessage, assistantName: String) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val emotion = message.emotion

    val userIconBitmap = remember {
        CustomAssetManager.loadImageBitmap(context, CustomAssetManager.AssetType.ICON)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser && emotion != null) {
            Image(
                painter = painterResource(EmotionMapper.getDrawable(emotion)),
                contentDescription = assistantName,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) DeepRose
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        color = if (isUser) Color.White
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            if (userIconBitmap != null) {
                Image(
                    painter = BitmapPainter(userIconBitmap),
                    contentDescription = "User",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.icon),
                    contentDescription = "User",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
