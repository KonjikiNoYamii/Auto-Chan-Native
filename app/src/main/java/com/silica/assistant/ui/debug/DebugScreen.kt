package com.silica.assistant.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.debug.CommentDebugEntry
import com.silica.assistant.core.debug.CommentDebugger
import com.silica.assistant.core.debug.DebugTier
import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import org.koin.compose.koinInject

private val TierColors = mapOf(
    DebugTier.VISION to Color(0xFF2E7D32),
    DebugTier.TEXT_AI to Color(0xFF1565C0),
    DebugTier.APP_AI to Color(0xFFF57F17),
    DebugTier.ERROR to Color(0xFFC62828),
)

private val TierLabels = mapOf(
    DebugTier.VISION to "Vision AI",
    DebugTier.TEXT_AI to "Text AI",
    DebugTier.APP_AI to "App AI",
    DebugTier.ERROR to "Error",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val entries = remember { CommentDebugger.log }
    var entriesState by remember { mutableStateOf(entries) }
    val moodManager: MoodManager = koinInject()
    var userProfile by remember { mutableStateOf<UserProfileEntity?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            entriesState = CommentDebugger.log
            userProfile = moodManager.getProfile()
            kotlinx.coroutines.delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Silica", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { CommentDebugger.clear(); entriesState = emptyList() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus log")
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
        ) {
            // Affinity Stats Section
            userProfile?.let { profile ->
                AffinityDebugCard(profile)
            }

            if (entriesState.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada komentar AI.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(entriesState.reversed(), key = { "${it.timestamp}_${it.tier}" }) { entry ->
                        EntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AffinityDebugCard(profile: UserProfileEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Hubungan: ${profile.relationshipRoute}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "🔥 Streak: ${profile.currentStreak}",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(Icons.Default.Favorite, "Lvl ${profile.level}", "XP: ${profile.xp}", DeepRose)
                StatItem(Icons.Default.Mood, "Mood", "${(profile.mood * 100).toInt()}%", Color.Yellow)
                StatItem(Icons.Default.Bolt, "Stamina", "${(profile.stamina * 100).toInt()}%", Color.Cyan)
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EntryCard(entry: CommentDebugEntry) {
    val tierColor = TierColors[entry.tier] ?: Color.Gray
    val tierLabel = TierLabels[entry.tier] ?: "?"
    val timeAgo = formatTimeAgo(entry.timestamp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.durationMs / 1000}.${(entry.durationMs % 1000) / 100}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tierColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tierLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = tierColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (entry.contextHint != null) {
                Text(
                    text = "Fokus: ${entry.contextHint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.response != null) {
                Text(
                    text = entry.response,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "[NULL] ${entry.errorMessage ?: "Tidak ada respon"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (entry.screenshotUsed) {
                Text(
                    text = "📸 Screenshot digunakan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "baru saja"
        diff < 3600_000 -> "${diff / 60_000}m yang lalu"
        diff < 86400_000 -> "${diff / 3600_000}j yang lalu"
        else -> "${diff / 86400_000}h yang lalu"
    }
}
