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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.config.AssistantConfig
import com.silica.assistant.core.ssh.ShellSession
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
    defaultTab: Int = 0,
    onOpenFile: (String) -> Unit = {}
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
    var commandHistory = remember { mutableListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var showSudoDialog by remember { mutableStateOf(false) }
    var terminalShell by remember { mutableStateOf<ShellSession?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by remember { mutableStateOf(false) }
    var showUploadPicker by remember { mutableStateOf(false) }
    var showNewItemDialog by remember { mutableStateOf(false) }
    var newItemIsFolder by remember { mutableStateOf(false) }
    var pendingDownloadPath by remember { mutableStateOf("") }
    var showSecurityWarning by remember { mutableStateOf(false) }
    var warningAccepted by remember { mutableStateOf(false) }
    var pendingConnection by remember { mutableStateOf<SshConnection?>(null) }
    val isActive = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!connected && SshManager.hasSavedConnection(context)) {
            val saved = SshManager.loadSavedConnection(context)
            if (saved != null) {
                host = saved.host
                port = saved.port.toString()
                username = saved.username
                if (saved.password.isNotBlank()) {
                    password = saved.password
                    rememberPassword = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isActive.value = false
        }
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
                    passwordVisible = passwordVisible,
                    rememberPassword = rememberPassword,
                    onHostChange = { host = it },
                    onPortChange = { port = it },
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                    onRememberPasswordChange = { rememberPassword = it },
                    connecting = connecting,
                    onConnect = {
                        val trimmedHost = host.trim()
                        val trimmedUser = username.trim()
                        val trimmedPort = port.trim()
                        val trimmedPass = password.trim()
                        if (trimmedHost.isBlank() || trimmedUser.isBlank()) {
                            Toast.makeText(context, "Host and username required", Toast.LENGTH_SHORT).show()
                            return@ConnectionForm
                        }
                        val portNum = trimmedPort.toIntOrNull() ?: 22
                        val conn = SshConnection(
                            name = "$trimmedUser@$trimmedHost",
                            host = trimmedHost,
                            port = portNum,
                            username = trimmedUser,
                            password = trimmedPass
                        )
                        if (!AssistantConfig.sshWarningAcknowledged && !warningAccepted) {
                            pendingConnection = conn
                            showSecurityWarning = true
                        } else {
                            connecting = true
                            doConnect(context, handler, conn,
                                {
                                    connected = true
                                    SshManager.saveConnection(context, conn, rememberPassword)
                                },
                                { connecting = false },
                                { currentPath = it })
                        }
                    }
                )
            } else {
                // security bar
                val connId = remember { SshManager.getConnectionId() }
                var showUntrustDialog by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00FF88).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF00FF88)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Terhubung ke $connId",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF88),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showUntrustDialog = true }) {
                            Text("Lupakan", fontSize = 10.sp, color = Color(0xFF00FF88))
                        }
                    }
                }
                if (showUntrustDialog) {
                    AlertDialog(
                        onDismissRequest = { showUntrustDialog = false },
                        title = { Text("Lupakan Host Ini?") },
                        text = {
                            Text(
                                "Hapus status tepercaya host $connId.\n\n" +
                                "Gunakan ini SETELAH Anda reinstall OS laptop, " +
                                "agar koneksi berikutnya dianggap sebagai host baru."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                SshManager.clearKnownHost(context, host, port.toIntOrNull() ?: 22)
                                showUntrustDialog = false
                                Toast.makeText(context, "Host dilupakan", Toast.LENGTH_SHORT).show()
                            }) { Text("Lupakan") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUntrustDialog = false }) { Text("Batal") }
                        }
                    )
                }

                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Terminal") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Files") })
                }

                when (tab) {
                    0 -> TerminalTab(
                        output = terminalOutput,
                        input = terminalInput,
                        onInputChange = { terminalInput = it },
                        onExecute = {
                            if (terminalInput.isNotBlank()) {
                                val cmd = terminalInput
                                terminalInput = ""
                                commandHistory.add(cmd)
                                historyIndex = -1
                                
                                // Intercept 'code' command
                                if (cmd.startsWith("code ")) {
                                    val path = cmd.substringAfter("code ").trim()
                                    // Robust path resolution
                                    val fullPath = when {
                                        path.startsWith("/") -> path
                                        path.startsWith("./") -> "$currentPath/${path.substring(2)}"
                                        path == "." -> currentPath
                                        else -> if (currentPath.endsWith("/")) "$currentPath$path" else "$currentPath/$path"
                                    }
                                    onOpenFile(fullPath)
                                } else {
                                    terminalShell?.sendCommand(cmd)
                                }
                            }
                        },
                        onClear = { terminalOutput = "" },
                        onInterrupt = { terminalShell?.interrupt() },
                        onHistoryUp = {
                            if (commandHistory.isNotEmpty()) {
                                val newIdx = if (historyIndex == -1) commandHistory.size - 1 else (historyIndex - 1).coerceAtLeast(0)
                                historyIndex = newIdx
                                terminalInput = commandHistory[newIdx]
                            }
                        },
                        onHistoryDown = {
                            if (historyIndex != -1) {
                                historyIndex++
                                if (historyIndex >= commandHistory.size) {
                                    historyIndex = -1
                                    terminalInput = ""
                                } else {
                                    terminalInput = commandHistory[historyIndex]
                                }
                            }
                        }
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
                        onOpenFile = onOpenFile,
                        onUpload = { showUploadPicker = true },
                        onNewFile = { 
                            newItemIsFolder = false
                            showNewItemDialog = true 
                        },
                        onNewFolder = {
                            newItemIsFolder = true
                            showNewItemDialog = true
                        },
                        onDownload = { file ->
                            pendingDownloadPath = file.path
                            downloadLauncher.launch(file.name)
                        },
                        onRefresh = {
                            refreshFiles(currentPath, { files = it }, { filesLoading = it })
                        }
                    )
                }

                if (showNewItemDialog) {
                    var name by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showNewItemDialog = false },
                        title = { Text(if (newItemIsFolder) "Folder Baru" else "File Baru") },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nama") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (name.isNotBlank()) {
                                    val path = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                                    Thread {
                                        val res = if (newItemIsFolder) SshManager.createNewFolder(path) else SshManager.createNewFile(path)
                                        handler.post {
                                            res.onSuccess {
                                                showNewItemDialog = false
                                                refreshFiles(currentPath, { files = it }, { filesLoading = it })
                                            }.onFailure { e ->
                                                Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }.start()
                                }
                            }) { Text("Buat") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNewItemDialog = false }) { Text("Batal") }
                        }
                    )
                }

                LaunchedEffect(tab, currentPath) {
                    if (tab == 1) {
                        refreshFiles(currentPath, { files = it }, { filesLoading = it })
                    }
                }

                LaunchedEffect(tab, connected) {
                    if (tab == 0 && connected) {
                        if (!SshManager.isShellActive()) {
                            val shell = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                SshManager.openShell().getOrNull()
                            }
                            terminalShell = shell
                            val listener: (String) -> Unit = { data ->
                                handler.post {
                                    terminalOutput += data
                                    
                                    // Automatic Path Sync Logic
                                    // Look for patterns like user@host:path$
                                    val lastLines = terminalOutput.lines().takeLast(5)
                                    for (line in lastLines.reversed()) {
                                        val match = Regex("@[^:]+:([^$]+)\\$").find(line)
                                        if (match != null) {
                                            val detectedPath = match.groupValues[1]
                                            if (detectedPath.isNotBlank() && detectedPath != "~") {
                                                currentPath = detectedPath
                                            } else if (detectedPath == "~") {
                                                currentPath = SshManager.homePath
                                            }
                                        }
                                    }
                                }
                            }
                            shell?.addOutputListener(listener)
                            shell?.onSudoPrompt = {
                                handler.post { showSudoDialog = true }
                            }
                            shell?.onError = { msg ->
                                handler.post { terminalOutput += "\nError: $msg" }
                            }
                        } else {
                            val shell = SshManager.getShell()
                            terminalShell = shell
                            val listener: (String) -> Unit = { data ->
                                handler.post {
                                    terminalOutput += data
                                    
                                    // Automatic Path Sync Logic
                                    // Look for patterns like user@host:path$
                                    val lastLines = terminalOutput.lines().takeLast(5)
                                    for (line in lastLines.reversed()) {
                                        val match = Regex("@[^:]+:([^$]+)\\$").find(line)
                                        if (match != null) {
                                            val detectedPath = match.groupValues[1]
                                            if (detectedPath.isNotBlank() && detectedPath != "~") {
                                                currentPath = detectedPath
                                            } else if (detectedPath == "~") {
                                                currentPath = SshManager.homePath
                                            }
                                        }
                                    }
                                }
                            }
                            shell?.addOutputListener(listener)
                            shell?.onSudoPrompt = {
                                handler.post { showSudoDialog = true }
                            }
                            shell?.onError = { msg ->
                                handler.post { terminalOutput += "\nError: $msg" }
                            }
                            terminalOutput = shell?.getFullOutput() ?: ""
                        }
                    }
                }
            }
        }
    }

    if (showSudoDialog) {
        SudoPasswordDialog(onDismiss = { showSudoDialog = false }) { pwd ->
            terminalShell?.sendPassword(pwd)
            showSudoDialog = false
        }
    }

    if (showUploadPicker) {
        uploadLauncher.launch("*/*")
        showUploadPicker = false
    }

    // security warning dialog
    if (showSecurityWarning) {
        AlertDialog(
            onDismissRequest = {
                showSecurityWarning = false
                pendingConnection = null
            },
            icon = { Icon(Icons.Filled.Security, contentDescription = null, tint = DeepRose) },
            title = { Text("⚠️ Peringatan Keamanan SSH") },
            text = {
                Column {
                    Text(
                        "SSH akan memberikan akses penuh terminal ke laptop Anda. " +
                        "Harap perhatikan:",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Hanya terhubung ke laptop Anda sendiri", fontSize = 13.sp)
                    Text("• Jangan gunakan di WiFi publik / tidak dikenal", fontSize = 13.sp)
                    Text("• Host key tidak diverifikasi penuh (MITM risk)", fontSize = 13.sp)
                    Text("• Pastikan laptop tujuan benar-benar milik Anda", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = warningAccepted,
                            onCheckedChange = { warningAccepted = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Jangan tampilkan lagi", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (warningAccepted) {
                            AssistantConfig.sshWarningAcknowledged = true
                        }
                        showSecurityWarning = false
                        pendingConnection?.let { conn ->
                            connecting = true
                            doConnect(context, handler, conn,
                                {
                                    connected = true
                                    SshManager.saveConnection(context, conn, rememberPassword)
                                },
                                { connecting = false },
                                { currentPath = it })
                        }
                        pendingConnection = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) { Text("Saya Mengerti, Lanjutkan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSecurityWarning = false
                    pendingConnection = null
                }) { Text("Batal") }
            }
        )
    }
}

private fun doConnect(
    context: android.content.Context,
    handler: android.os.Handler,
    conn: SshConnection,
    onConnected: (Boolean) -> Unit,
    onFinish: () -> Unit,
    setCurrentPath: (String) -> Unit
) {
    Thread {
        SshManager.connect(conn, context)
            .onSuccess {
                handler.post {
                    onConnected(true)
                    setCurrentPath(SshManager.homePath)
                    refreshFiles(SshManager.homePath, { _ -> }, { _ -> })
                }
            }
            .onFailure { e ->
                handler.post {
                    Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        handler.post { onFinish() }
    }.start()
}

@Composable
private fun ConnectionForm(
    host: String,
    port: String,
    username: String,
    password: String,
    passwordVisible: Boolean,
    connecting: Boolean,
    rememberPassword: Boolean = false,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onRememberPasswordChange: (Boolean) -> Unit = {},
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
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Sembunyikan" else "Tampilkan"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None
            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberPassword,
                onCheckedChange = onRememberPasswordChange,
                colors = CheckboxDefaults.colors(checkedColor = DeepRose)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Ingat password",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
    onInterrupt: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit
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
            var currentPos = 0
            // Broadest possible regex to catch CSI ([), OSC (]), and other control sequences
            val ansiRegex = Regex("\\u001B\\[[?;\\d]*[A-Za-z]|\\u001B\\][0-9];.*?(\\u0007|\\u001B\\\\)|\\u001B.")
            val matches = ansiRegex.findAll(output).toList()
            
            var currentColor = outputColor
            
            for (match in matches) {
                // Style text before the escape code
                val textBefore = output.substring(currentPos, match.range.first)
                if (textBefore.isNotEmpty()) {
                    withStyle(SpanStyle(color = currentColor)) {
                        append(textBefore)
                    }
                }
                
                val ansiCode = match.value
                // Process only color sequences
                if (ansiCode.startsWith("\u001B[") && ansiCode.endsWith("m")) {
                    val content = ansiCode.substring(2, ansiCode.length - 1)
                    if (content.isEmpty() || content == "0" || content == "00") {
                        currentColor = outputColor
                    } else {
                        val codes = content.split(";")
                        for (rawCode in codes) {
                            val code = rawCode.trim().removePrefix("0").ifEmpty { "0" }
                            when (code) {
                                "0" -> currentColor = outputColor
                                "31" -> currentColor = errorColor
                                "32" -> currentColor = cmdColor
                                "33" -> currentColor = promptColor
                                "34" -> currentColor = Color(0xFF569CD6) // Blue
                                "35" -> currentColor = Color(0xFFC586C0) // Magenta
                                "36" -> currentColor = Color(0xFF4EC9B0) // Cyan
                                "91" -> currentColor = Color(0xFFFF5555) // Bright Red
                                "92" -> currentColor = Color(0xFF55FF55) // Bright Green
                                "93" -> currentColor = Color(0xFFFFFF55) // Bright Yellow
                                "94" -> currentColor = Color(0xFF5555FF) // Bright Blue
                            }
                        }
                    }
                }
                // Anything else starting with ESC (\u001B) is effectively hidden here
                currentPos = match.range.last + 1
            }
            
            // Remaining text
            if (currentPos < output.length) {
                withStyle(SpanStyle(color = currentColor)) {
                    append(output.substring(currentPos))
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
                text = "Terminal",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = DeepRose,
                maxLines = 1
            )
            Row {
                TextButton(onClick = onInterrupt) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = DeepRose)
                    Spacer(Modifier.width(4.dp))
                    Text("Ctrl+C", color = DeepRose)
                }
                TextButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
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

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onHistoryUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "History Up", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onHistoryDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "History Down", modifier = Modifier.size(20.dp))
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
    onOpenFile: (String) -> Unit,
    onUpload: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onDownload: (SshFile) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val downloadDir = remember {
        File(context.getExternalFilesDir(null), "SilicaScripts").apply { if (!exists()) mkdirs() }
    }

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
            IconButton(onClick = onNewFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onNewFile) {
                Icon(Icons.Filled.NoteAdd, contentDescription = "New File", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onUpload) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Upload", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
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
                    val isDownloaded = File(downloadDir, file.name).exists()
                    
                    FileRow(
                        icon = if (file.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        name = file.name,
                        isDir = file.isDirectory,
                        size = file.size,
                        isDownloaded = isDownloaded,
                        onClick = {
                            if (file.isDirectory) {
                                onNavigate(file.path)
                            } else {
                                onOpenFile(file.path)
                            }
                        },
                        onDownload = if (!file.isDirectory) {
                            {
                                val localFile = File(downloadDir, file.name)
                                Thread {
                                    SshManager.downloadFile(file.path, localFile.absolutePath)
                                        .onSuccess {
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                Toast.makeText(context, "Saved to SilicaScripts", Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            }
                                        }
                                        .onFailure { e ->
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }.start()
                            }
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
    isDownloaded: Boolean = false,
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
                tint = if (isDir) DeepRose else if (isDownloaded) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 14.sp)
                    if (isDownloaded) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00FF88), modifier = Modifier.size(12.dp))
                    }
                }
                if (!isDir) {
                    Text(
                        formatSize(size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDownload != null && !isDownloaded) {
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

@Composable
private fun SudoPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = DeepRose) },
        title = { Text("Sudo Password") },
        text = {
            OutlinedTextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = { Text("Password") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pwd) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
            ) { Text("Kirim") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
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
