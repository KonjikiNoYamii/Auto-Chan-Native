package com.silica.assistant.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.CommandHistoryManager
import com.silica.assistant.core.CommandManager
import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.overlay.WaifuStateManager
import com.silica.assistant.ui.theme.DeepRose
import org.koin.compose.koinInject

@Composable
fun DashboardSection(
    commandText: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val moodManager: MoodManager = koinInject()
    var overlayActive by remember { mutableStateOf(false) }
    var sshHost by remember { mutableStateOf("") }
    var sshConnected by remember { mutableStateOf(false) }
    var affinityPoints by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var affinityLabel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            sshConnected = SshManager.isConnected()
            sshHost = SshManager.getCurrentConnection()?.host
                ?: SshManager.getConnectionId()
                ?: ""
            overlayActive = OverlayEventBus.onBubble != null
            val profile = moodManager.getProfile()
            affinityPoints = profile.affinityPoints
            level = profile.level
            affinityLabel = when (moodManager.getAffinityLevel()) {
                "STRANGER" -> "Asing"
                "ACQUAINTANCE" -> "Kenalan"
                "FRIEND" -> "Teman"
                "CLOSE_FRIEND" -> "Dekat"
                "LOVER" -> "Sayang"
                "SOULMATE" -> "Jiwa"
                else -> moodManager.getAffinityLevel()
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepRose)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                icon = Icons.Default.Computer,
                title = "SSH Laptop",
                value = if (sshConnected) "Online • $sshHost" else "Offline",
                valueColor = if (sshConnected) Color(0xFF4CAF50) else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                icon = Icons.Default.Favorite,
                title = "Waifu Mood",
                value = "$affinityLabel • Lv.$level",
                valueColor = DeepRose,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(
                icon = Icons.Default.Visibility,
                title = "Overlay",
                value = if (overlayActive) "Aktif" else "Tidak Aktif",
                valueColor = if (overlayActive) Color(0xFF4CAF50) else Color.Gray,
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        CommandManager.execute(context, if (overlayActive) "stop_overlay" else "start_overlay")
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            DashboardCard(
                icon = Icons.Default.Terminal,
                title = "Commands",
                value = "${CommandHistoryManager.logs.size} total",
                onClick = { onNavigate("debug") },
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = onCommandChange,
                    placeholder = { Text("Tulis command...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onExecute,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = commandText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Execute")
                }
            }
        }

        if (CommandHistoryManager.logs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = DeepRose, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recent Commands", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    CommandHistoryManager.logs.takeLast(5).forEach { log ->
                        Text("◈ ${log.text}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    icon: ImageVector,
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DeepRose.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
            }
        }
    }
}
