package com.silica.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.debug.CommentDebugEntry
import com.silica.assistant.core.debug.CommentDebugger
import com.silica.assistant.core.debug.DebugTier
import com.silica.assistant.core.llm.LlmClient

private val TierColors = mapOf(
    DebugTier.VISION to Color(0xFF2E7D32),
    DebugTier.TEXT_AI to Color(0xFF1565C0),
    DebugTier.APP_AI to Color(0xFFF57F17),
    DebugTier.ERROR to Color(0xFFC62828),
)

private val TierLabels = mapOf(
    DebugTier.VISION to "Vision",
    DebugTier.TEXT_AI to "Text",
    DebugTier.APP_AI to "App",
    DebugTier.ERROR to "Err",
)

@Composable
fun DebugOutputSection() {
    var latestEntries by remember { mutableStateOf(CommentDebugger.log.takeLast(10)) }
    var activeProvider by remember { mutableStateOf(LlmClient.activeProvider) }

    LaunchedEffect(Unit) {
        while (true) {
            latestEntries = CommentDebugger.log.takeLast(10)
            activeProvider = LlmClient.activeProvider
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Debug Output (AI)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Provider: $activeProvider",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (latestEntries.isEmpty()) {
            Text(
                text = "Belum ada log AI...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(latestEntries.reversed().size) { index ->
                        val entry = latestEntries.reversed()[index]
                        DebugEntryItem(entry)
                        if (index < latestEntries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugEntryItem(entry: CommentDebugEntry) {
    val tierColor = TierColors[entry.tier] ?: Color.Gray
    val tierLabel = TierLabels[entry.tier] ?: "?"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.appName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.provider != null) {
                    Text(
                        text = entry.provider,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tierColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = tierLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = tierColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = entry.response ?: "[NULL] ${entry.errorMessage ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.response != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
