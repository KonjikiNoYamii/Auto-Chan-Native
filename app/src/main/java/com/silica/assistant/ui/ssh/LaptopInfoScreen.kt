package com.silica.assistant.ui.ssh

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.ui.theme.DeepRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopInfoScreen(
    onBack: () -> Unit
) {
    val handler = remember { Handler(Looper.getMainLooper()) }
    var uptime by remember { mutableStateOf("—") }
    var memory by remember { mutableStateOf("—") }
    var disk by remember { mutableStateOf("—") }
    var connected by remember { mutableStateOf(SshManager.isConnected()) }
    val scroll = rememberScrollState()
    val isActive = remember { mutableStateOf(true) }

    // polling thread instead of persistent exec channel
    DisposableEffect(Unit) {
        Thread {
            while (isActive.value) {
                val wasConnected = SshManager.isConnected()
                handler.post { if (isActive.value) connected = wasConnected }
                if (wasConnected) {
                    val u = SshManager.executeCommand("uptime -p 2>/dev/null || echo '?'")
                    val m = SshManager.executeCommand("free -h 2>/dev/null | head -3")
                    val d = SshManager.executeCommand("df -h / 2>/dev/null | tail -1")
                    handler.post {
                        if (isActive.value) {
                            u.onSuccess { uptime = it.trim() }
                            m.onSuccess { memory = it.trim() }
                            d.onSuccess { disk = it.trim() }
                        }
                    }
                }
                Thread.sleep(3000)
            }
        }.apply { start() }
        onDispose { isActive.value = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laptop Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .background(Color(0xFF1A1A2E))
                .verticalScroll(scroll)
                .padding(16.dp)
        ) {
            if (!connected) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SSH tidak terhubung",
                        color = Color(0xFF888888),
                        fontSize = 16.sp
                    )
                }
                return@Scaffold
            }

            InfoCard(
                icon = Icons.Filled.Timer,
                title = "Uptime",
                content = uptime,
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(
                icon = Icons.Filled.Memory,
                title = "Memory",
                content = memory,
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(
                icon = Icons.Filled.Storage,
                title = "Disk",
                content = disk,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Real-time polling via SSH",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF16213E)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = DeepRose, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = DeepRose,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = Color(0xFF00FF88),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}
