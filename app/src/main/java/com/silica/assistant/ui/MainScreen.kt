package com.silica.assistant.ui

import android.Manifest
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silica.assistant.R
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.config.TutorialManager

import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.screen.ScreenCaptureManager
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.overlay.WaifuState
import com.silica.assistant.overlay.WaifuStateManager
import com.silica.assistant.core.update.UpdateChecker
import com.silica.assistant.core.update.UpdateDownloader
import com.silica.assistant.core.update.UpdateInstaller
import com.silica.assistant.ui.components.*
import com.silica.assistant.ui.components.UpdateDialog
import com.silica.assistant.ui.customize.CustomizeScreen
import com.silica.assistant.ui.guide.GuideScreen
import com.silica.assistant.ui.tutorial.OverlayTutorialScreen
import com.silica.assistant.ui.chat.ChatScreen
import com.silica.assistant.ui.debug.DebugScreen
import com.silica.assistant.ui.ssh.LaptopInfoScreen
import com.silica.assistant.ui.ssh.SshScreen
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private sealed class Screen {
    data object Main : Screen()
    data class Ssh(val tab: Int = 0) : Screen()
    data object Info : Screen()
    data object Guide : Screen()
    data object Customize : Screen()
    data object OverlayTutorial : Screen()
    data object Chat : Screen()
    data object Debug : Screen()
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: AssistantViewModel = viewModel()
    val uiState = viewModel.uiState
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var pendingAudioPermission by remember { mutableStateOf(false) }
    var screenCaptureLaunched by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showUsagePermissionDialog by remember { mutableStateOf(false) }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            WaifuStateManager.currentState = WaifuState.LISTEN
            viewModel.setListening(true)
            VoiceManager.start()
        } else {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk voice command", Toast.LENGTH_SHORT).show()
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ScreenCaptureManager.init(context)
        ScreenCaptureManager.resultCode = result.resultCode
        ScreenCaptureManager.resultData = result.data

        when {
            result.resultCode != android.app.Activity.RESULT_OK -> {
                screenCaptureLaunched = false
                Toast.makeText(context, "Izin screen capture diperlukan untuk overlay", Toast.LENGTH_LONG).show()
            }
            result.data == null -> {
                screenCaptureLaunched = false
                Toast.makeText(context, "Gagal mendapatkan izin screen capture", Toast.LENGTH_LONG).show()
            }
            else -> {
                // Izin diberikan. Coba inisialisasi langsung.
                // Kalau gagal (butuh foreground service), overlay akan init via tryRestore().
                try {
                    ScreenCaptureManager.setupProjection(context)
                } catch (_: Exception) { }
                if (ScreenCaptureManager.isReady()) {
                    Toast.makeText(context, "Screen capture siap", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // 1. Check Audio
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudioPermission = true
        }
        
        // 2. Check Overlay
        if (!android.provider.Settings.canDrawOverlays(context)) {
            showOverlayPermissionDialog = true
        }

        // 3. Check Usage Stats
        val activityDetector = com.silica.assistant.core.ActivityDetector(context)
        if (!activityDetector.isUsageStatsGranted()) {
            showUsagePermissionDialog = true
        }

        // 4. Screen Capture — init & request once
        ScreenCaptureManager.init(context)
        if (!ScreenCaptureManager.isReady() && !screenCaptureLaunched) {
            screenCaptureLaunched = true
            delay(100)
            try {
                val mgr = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                        as android.media.projection.MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "screen capture initial launch failed", e)
            }
        }
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text("Izin Overlay") },
            text = { Text("Silica butuh izin untuk tampil di atas aplikasi lain agar waifu kamu bisa nemenin terus~") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPermissionDialog = false
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Buka Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) { Text("Nanti") }
            }
        )
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text("Akses Penggunaan") },
            text = { Text("Biar Silica tahu kamu lagi main game apa dan bisa kasih semangat, izinkan akses penggunaan aplikasi ya~") },
            confirmButton = {
                TextButton(onClick = {
                    showUsagePermissionDialog = false
                    val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }) { Text("Buka Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showUsagePermissionDialog = false }) { Text("Nanti") }
            }
        )
    }

    BackHandler(enabled = currentScreen !is Screen.Main) { currentScreen = Screen.Main }

    LaunchedEffect(Unit) {
        snapshotFlow { OverlayEventBus.navigateScreen.value }.collect { dest ->
            if (dest != null) {
                if (dest == "request_audio_permission") {
                    currentScreen = Screen.Main
                    OverlayEventBus.navigateScreen.value = null
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingAudioPermission = true
                    }
                } else if (dest == "request_screen_capture") {
                    currentScreen = Screen.Main
                    OverlayEventBus.navigateScreen.value = null
                    if (!ScreenCaptureManager.isReady() && !screenCaptureLaunched) {
                        screenCaptureLaunched = true
                        try {
                            val mgr = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                                    as android.media.projection.MediaProjectionManager
                            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                        } catch (e: Exception) {
                            android.util.Log.e("MainScreen", "screen capture overlay launch failed", e)
                        }
                    }
                } else {
                    currentScreen =
                            when (dest) {
                                "ssh" -> Screen.Ssh(tab = 0)
                                "chat" -> Screen.Chat
                                "debug" -> Screen.Debug
                                else -> Screen.Main
                            }
                    OverlayEventBus.navigateScreen.value = null
                }
                // bring app to foreground when triggered from overlay
                val launchIntent =
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    context.startActivity(launchIntent)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return@LaunchedEffect
        val currentVersionCode = try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkg).toInt()
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "Failed to get version code", e)
            0
        }
        val info = UpdateChecker.check(currentVersionCode)
        if (info != null) {
            updateInfo = info
        }
    }

    LaunchedEffect(Unit) {
        if (!TutorialManager.isOverlayTutorialDone(context)) {
            currentScreen = Screen.OverlayTutorial
        }
    }

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    val greeting = remember {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        CustomAssetManager.getCustomGreeting(context, hour)
                ?: when {
                    hour < 12 -> "Selamat pagi"
                    hour < 15 -> "Selamat siang"
                    hour < 18 -> "Selamat sore"
                    else -> "Selamat malam"
                }
    }

    LaunchedEffect(pendingAudioPermission) {
        if (pendingAudioPermission) {
            pendingAudioPermission = false
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    when (currentScreen) {
        is Screen.Main -> {
            Box(
                    modifier =
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HeaderSection(greeting = greeting)

                    Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QuickActionChips(
                                onChipClick = { label ->
                                    when (label) {
                                        "Terminal" -> currentScreen = Screen.Ssh(tab = 0)
                                        "File" -> currentScreen = Screen.Ssh(tab = 1)
                                        "SSH" -> currentScreen = Screen.Ssh(tab = 0)
                                        "Info" -> {
                                            if (!SshManager.isConnected()) {
                                                Toast.makeText(
                                                                context,
                                                                "SSH not connected",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                return@QuickActionChips
                                            }
                                            currentScreen = Screen.Info
                                        }
                                        "Chat" -> currentScreen = Screen.Chat
                                        "Guide" -> currentScreen = Screen.Guide
                                        "Customize" -> currentScreen = Screen.Customize
                                        "Debug" -> currentScreen = Screen.Debug
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        StatusBar()

                        Spacer(modifier = Modifier.height(16.dp))

                        CommandInputSection(
                                commandText = uiState.commandText,
                                onCommandChange = { viewModel.updateCommandText(it) },
                                onExecute = {
                                    if (uiState.commandText.isNotBlank()) {
                                        WaifuStateManager.currentState = WaifuState.TALK
                                        CommandManager.execute(context, uiState.commandText)
                                        handler.postDelayed(
                                                {
                                                    WaifuStateManager.currentState =
                                                            WaifuState.RELAX
                                                },
                                                3000
                                        )
                                        viewModel.clearCommand()
                                    }
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CommandHistorySection()

                        Spacer(modifier = Modifier.height(16.dp))

                        DebugOutputSection()

                        Spacer(modifier = Modifier.height(16.dp))

                        OverlayControlSection()

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
        is Screen.Ssh -> {
            SshScreen(
                    onBack = { currentScreen = Screen.Main },
                    defaultTab = (currentScreen as Screen.Ssh).tab
            )
        }
        is Screen.Info -> {
            LaptopInfoScreen(onBack = { currentScreen = Screen.Main })
        }
        is Screen.Guide -> {
            GuideScreen(onBack = { currentScreen = Screen.Main })
        }
        is Screen.Customize -> {
            CustomizeScreen(onBack = { currentScreen = Screen.Main })
        }
        is Screen.OverlayTutorial -> {
            OverlayTutorialScreen(
                onDone = {
                    TutorialManager.markOverlayTutorialDone(context)
                    currentScreen = Screen.Main
                }
            )
        }
        is Screen.Chat -> {
            ChatScreen(onBack = { currentScreen = Screen.Main })
        }
        is Screen.Debug -> {
            DebugScreen(onBack = { currentScreen = Screen.Main })
        }
    }

    val currentVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }

    val scope = rememberCoroutineScope()

    updateInfo?.let { info ->
        UpdateDialog(
            currentVersion = currentVersionName,
            newVersion = info.latestVersionName,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onUpdate = {
                isDownloading = true
                scope.launch {
                    val result = UpdateDownloader.download(context, info.downloadUrl) { progress ->
                        downloadProgress = progress
                    }
                    if (result != null) {
                        isDownloading = false
                        UpdateInstaller.install(context, result.file)
                    } else {
                        isDownloading = false
                        android.widget.Toast
                            .makeText(context, "Gagal mengunduh update", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            },
            onLater = { updateInfo = null }
        )
    }
}

@Composable
private fun HeaderSection(greeting: String) {
    val context = LocalContext.current
    val headerBitmap = remember {
        CustomAssetManager.loadImageBitmap(context, CustomAssetManager.AssetType.HEADER)
    }
    val iconBitmap = remember {
        CustomAssetManager.loadImageBitmap(context, CustomAssetManager.AssetType.ICON)
    }

    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
        if (headerBitmap != null) {
            Image(
                    painter = BitmapPainter(headerBitmap),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
            )
        } else {
            Image(
                    painter = painterResource(id = R.drawable.header),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
            )
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                Color(0x80000000),
                                                                Color(0x00000000),
                                                                Color(0xFFF2EAE1)
                                                        )
                                        )
                                )
        )

        Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                        painter = BitmapPainter(iconBitmap),
                        contentDescription = "Waifu",
                        modifier =
                                Modifier.size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                )
            } else {
                Image(
                        painter = painterResource(id = R.drawable.icon),
                        contentDescription = "Waifu",
                        modifier =
                                Modifier.size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                )
            }

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
private fun QuickActionChips(onChipClick: (String) -> Unit = {}) {
    val chips =
            listOf(
                    ChipData("Terminal", Icons.Filled.Terminal, DeepRose),
                    ChipData("File", Icons.Filled.Folder, DeepRose),
                    ChipData("SSH", Icons.Filled.Lan, DeepRose),
                    ChipData("Chat", Icons.Filled.QuestionAnswer, DeepRose),
                    ChipData("Info", Icons.Filled.Info, DeepRose),
                    ChipData("Guide", Icons.AutoMirrored.Filled.MenuBook, DeepRose),
            ChipData("Customize", Icons.Filled.Palette, DeepRose),
            ChipData("Debug", Icons.Filled.BugReport, DeepRose),
    )

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { chip ->
            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                FilledIconButton(
                        onClick = { onChipClick(chip.label) },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = chip.color.copy(alpha = 0.15f),
                                        contentColor = chip.color
                                )
                ) {
                    Icon(
                            chip.icon,
                            contentDescription = chip.label,
                            modifier = Modifier.size(24.dp)
                    )
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
    val sshConnected = SshManager.isConnected()
    val sshLabel =
            if (sshConnected) {
                val conn = SshManager.getCurrentConnection()
                "Laptop: ${conn?.host ?: "online"}"
            } else {
                "Laptop: offline"
            }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            StatusItem(Icons.Filled.Wifi, "WiFi")
            StatusItem(Icons.Filled.Computer, sshLabel)
            StatusItem(Icons.Filled.Storage, if (sshConnected) "SSH: ✓" else "SSH: --")
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
