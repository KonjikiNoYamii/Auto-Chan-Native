package com.silica.assistant.ui.ssh

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.ssh.SshConnection
import com.silica.assistant.core.ssh.SshFile
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import java.io.File

private var initialTab = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshScreen(
    onBack: () -> Unit,
    defaultTab: Int = 0
) {
    initialTab = defaultTab
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var connected by remember { mutableStateOf(SshManager.isConnected()) }
    var tab by remember { mutableIntStateOf(defaultTab) }

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<SshFile>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var terminalOutput by remember { mutableStateOf("") }
    var terminalInput by remember { mutableStateOf("") }
    var terminalDir by remember { mutableStateOf("") }

    var showUploadPicker by remember { mutableStateOf(false) }
    var pendingDownloadPath by remember { mutableStateOf("") }
    val isActive = remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { isActive.value = false }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Thread {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}")
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        inputStream.close()
                        val result = SshManager.uploadFile(tempFile.absolutePath, "$currentPath/${tempFile.name}")
                        tempFile.delete()
                        handler.post {
                            result.onSuccess {
                                Toast.makeText(context, "Uploaded", Toast.LENGTH_SHORT).show()
                                refreshFiles(currentPath, { files = it }, { filesLoading = it })
                            }.onFailure { e ->
                                Toast.makeText(context, "Upload gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                if (!SshManager.isConnected()) connected = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { downloadUri: Uri? ->
        downloadUri?.let { uri ->
            val remotePath = pendingDownloadPath
            if (remotePath.isNotBlank()) {
                pendingDownloadPath = ""
                Thread {
                    val temp = File(context.cacheDir, "download_${System.currentTimeMillis()}")
                    SshManager.downloadFile(remotePath, temp.absolutePath)
                        .onSuccess {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                temp.inputStream().use { inp -> inp.copyTo(out) }
                            }
                            temp.delete()
                            handler.post {
                                Toast.makeText(context, "Downloaded", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .onFailure { e ->
                            temp.delete()
                            handler.post {
                                Toast.makeText(context, "Download gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                if (!SshManager.isConnected()) connected = false
                            }
                        }
                }.start()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (connected) {
                        IconButton(onClick = {
                            SshManager.disconnect()
                            connected = false
                        }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect")
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
            if (!connected) {
                ConnectionForm(
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    onHostChange = { host = it },
                    onPortChange = { port = it },
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    connecting = connecting,
                    onConnect = {
                        if (host.isBlank() || username.isBlank()) {
                            Toast.makeText(context, "Host and username required", Toast.LENGTH_SHORT).show()
                            return@ConnectionForm
                        }
                        connecting = true
                        val portNum = port.toIntOrNull() ?: 22
                        val conn = SshConnection(
                            name = "$username@$host",
                            host = host,
                            port = portNum,
                            username = username,
                            password = password
                        )
                        Thread {
                            SshManager.connect(conn, context)
                                .onSuccess {
                                    connected = true
                                    terminalDir = SshManager.homePath
                                    currentPath = SshManager.homePath
                                    refreshFiles(currentPath, { files = it }, { filesLoading = it })
                                }
                                .onFailure { e ->
                                    handler.post {
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            connecting = false
                        }.start()
                    }
                )
            } else {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Terminal") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Files") })
                }

                when (tab) {
                        0 -> TerminalTab(
                        output = terminalOutput,
                        input = terminalInput,
                        currentDir = terminalDir,
                        onInputChange = { terminalInput = it },
                        onExecute = {
                            if (terminalInput.isNotBlank()) {
                                val rawCmd = terminalInput
                                terminalInput = ""
                                val home = SshManager.homePath
                                // resolve cd locally
                                if (rawCmd.startsWith("cd ")) {
                                    val target = rawCmd.removePrefix("cd ").trim()
                                    val resolved = resolvePath(terminalDir, target, home)
                                    terminalDir = resolved
                                    terminalOutput = if (terminalOutput.isEmpty()) "" else "$terminalOutput\n"
                                    terminalOutput = "$terminalOutput${buildPrompt(resolved)}"
                                    // refresh files tab path too
                                    currentPath = resolved
                                    refreshFiles(resolved, { files = it }, { filesLoading = it })
                                } else {
                                    val prompt = buildPrompt(terminalDir)
                                    val display = "$prompt$rawCmd"
                                    terminalOutput = if (terminalOutput.isEmpty()) display else "$terminalOutput\n$display"
                                    val fullCmd = "cd '$terminalDir' 2>/dev/null; $rawCmd"
                                    Thread {
                                        val cmdResult = SshManager.executeCommand(fullCmd)
                                        handler.post {
                                            if (isActive.value) {
                                                cmdResult
                                                    .onSuccess { result ->
                                                        terminalOutput = "$terminalOutput\n$result"
                                                    }
                                                    .onFailure { e ->
                                                        terminalOutput = "$terminalOutput\nError: ${e.message}"
                                                        handler.post {
                                                            if (!SshManager.isConnected()) connected = false
                                                        }
                                                    }
                                            }
                                        }
                                    }.start()
                                }
                            }
                        },
                        onClear = { terminalOutput = "" }
                    )
                    1 -> FilesTab(
                        currentPath = currentPath,
                        homePath = SshManager.homePath,
                        files = files,
                        loading = filesLoading,
                        onNavigate = { dir ->
                            currentPath = dir
                            refreshFiles(dir, { files = it }, { filesLoading = it })
                        },
                        onUpload = { showUploadPicker = true },
                        onDownload = { file ->
                            pendingDownloadPath = file.path
                            downloadLauncher.launch(file.name)
                        },
                        onRefresh = {
                            refreshFiles(currentPath, { files = it }, { filesLoading = it })
                        }
                    )
                }
            }
        }
    }

    if (showUploadPicker) {
        uploadLauncher.launch("*/*")
        showUploadPicker = false
    }
}

@Composable
private fun ConnectionForm(
    host: String,
    port: String,
    username: String,
    password: String,
    connecting: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Filled.Lan,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = DeepRose
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Connect to Laptop",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Espresso
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Masukkan detail koneksi SSH laptop Anda",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Host / IP") },
            leadingIcon = { Icon(Icons.Filled.Computer, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Port") },
            leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !connecting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (connecting) "Connecting..." else "Connect",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun TerminalTab(
    output: String,
    input: String,
    currentDir: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit
) {
    val scrollState = rememberScrollState()
    val promptColor = Color(0xFFFFD700)
    val cmdColor = Color(0xFF00FF88)
    val outputColor = Color(0xFFCCCCCC)
    val errorColor = Color(0xFFFF6B6B)

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val annotated = remember(output) {
        if (output.isEmpty()) return@remember null
        buildAnnotatedString {
            val lines = output.split("\n")
            for ((i, line) in lines.withIndex()) {
                if (i > 0) append("\n")
                when {
                    line.startsWith("Error:") || line.startsWith("❌") ->
                        withStyle(SpanStyle(color = errorColor)) { append(line) }
                    line.contains(" $ ") || line.contains("$ ") -> {
                        val idx = line.indexOf("$ ")
                        withStyle(SpanStyle(color = promptColor)) { append(line.substring(0, idx + 1)) }
                        withStyle(SpanStyle(color = cmdColor)) { append(line.substring(idx + 1)) }
                    }
                    else ->
                        withStyle(SpanStyle(color = outputColor)) { append(line) }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentDir.isNotEmpty()) currentDir else "Output",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = DeepRose,
                maxLines = 1
            )
            TextButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    Color(0xFF1A1A2E),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .verticalScroll(scrollState)
        ) {
            if (annotated != null) {
                Text(
                    text = annotated,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Terminal siap. Ketik perintah di bawah.",
                    color = cmdColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik perintah...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onExecute,
                modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DeepRose)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Execute")
            }
        }
    }
}

@Composable
private fun FilesTab(
    currentPath: String,
    homePath: String,
    files: List<SshFile>,
    loading: Boolean,
    onNavigate: (String) -> Unit,
    onUpload: () -> Unit,
    onDownload: (SshFile) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentPath,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onUpload) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload")
            }
        }

        if (homePath.isNotEmpty()) {
            QuickFolders(homePath = homePath, currentPath = currentPath, onNavigate = onNavigate)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Folder kosong atau tidak bisa dibaca", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (currentPath != "/") {
                    item {
                        FileRow(
                            icon = Icons.Filled.FolderOpen,
                            name = "..",
                            isDir = true,
                            size = 0,
                            onClick = {
                                val parent = currentPath.substringBeforeLast("/")
                                onNavigate(if (parent.isEmpty()) "/" else parent)
                            }
                        )
                    }
                }
                items(files) { file ->
                    FileRow(
                        icon = if (file.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        name = file.name,
                        isDir = file.isDirectory,
                        size = file.size,
                        onClick = {
                            if (file.isDirectory) {
                                onNavigate(file.path)
                            }
                        },
                        onDownload = if (!file.isDirectory) {
                            { onDownload(file) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFolders(
    homePath: String,
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val folders = remember(homePath) {
        listOf(
            QuickFolder("Home", Icons.Filled.Home, homePath),
            QuickFolder("Documents", Icons.Filled.Description, "$homePath/Documents"),
            QuickFolder("Downloads", Icons.Filled.Download, "$homePath/Downloads"),
            QuickFolder("Pictures", Icons.Filled.Image, "$homePath/Pictures"),
            QuickFolder("Music", Icons.Filled.MusicNote, "$homePath/Music"),
            QuickFolder("Videos", Icons.Filled.VideoFile, "$homePath/Videos"),
            QuickFolder("Desktop", Icons.Filled.DesktopWindows, "$homePath/Desktop"),
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            folders.forEach { folder ->
                val isActive = currentPath == folder.path
                AssistChip(
                    onClick = { onNavigate(folder.path) },
                    label = { Text(folder.label, fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(folder.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isActive) DeepRose.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private data class QuickFolder(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val path: String
)

@Composable
private fun FileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    isDir: Boolean,
    size: Long,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isDir) DeepRose else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp)
                if (!isDir) {
                    Text(
                        formatSize(size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val k = bytes / 1024.0
    if (k < 1024) return "%.1f KB".format(k)
    val m = k / 1024.0
    if (m < 1024) return "%.1f MB".format(m)
    return "%.1f GB".format(m / 1024.0)
}

private fun displayPath(path: String): String {
    val home = SshManager.homePath
    return when {
        path == home -> "~"
        path.startsWith(home) -> path.replace(home, "~")
        else -> path
    }
}

private fun buildPrompt(path: String): String {
    val conn = SshManager.getCurrentConnection()
    val user = conn?.username ?: "user"
    val host = conn?.host ?: "host"
    return "$user@$host:${displayPath(path)}$ "
}

private fun resolvePath(current: String, target: String, home: String): String {
    if (target == "~" || target == "~/") return home
    val abs = when {
        target == ".." -> current.substringBeforeLast("/").let { if (it.isEmpty()) "/" else it }
        target.startsWith("/") -> target
        target.startsWith("~/") -> target.replaceFirst("~", home)
        target.startsWith("./") -> {
            val base = if (current.endsWith("/")) current else "$current/"
            "$base${target.removePrefix("./")}"
        }
        target.startsWith("..") -> {
            var dir = current
            var rest = target
            while (rest.startsWith("..")) {
                dir = dir.substringBeforeLast("/").let { if (it.isEmpty()) "/" else it }
                rest = rest.removePrefix("..").removePrefix("/")
            }
            if (rest.isEmpty()) dir else "${dir.removeSuffix("/")}/$rest"
        }
        else -> "${current.removeSuffix("/")}/$target"
    }
    return abs.removeSuffix("/").let { if (it.isEmpty()) "/" else it }
}

private fun refreshFiles(
    path: String,
    onResult: (List<SshFile>) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    onLoading(true)
    Thread {
        val result = SshManager.listFiles(path)
        result.onSuccess { list -> onResult(list) }
        result.onFailure { onResult(emptyList()) }
        onLoading(false)
    }.start()
}
