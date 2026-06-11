package com.silica.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.silica.assistant.core.llm.model.QuestEntity
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso

@Composable
fun QuestSection(
    activeQuests: List<QuestEntity>,
    onAddQuest: (String, String) -> Unit,
    onCompleteQuest: (String) -> Unit,
    onDeleteQuest: (QuestEntity) -> Unit,
    onClassify: suspend (String) -> String,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Personal Quests", Icons.Default.Assignment)
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(32.dp).background(DeepRose.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Quest", tint = DeepRose, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (activeQuests.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(40.dp), tint = Color.Gray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Semua tugas selesai! ♪",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    activeQuests.forEachIndexed { index, quest ->
                        QuestItem(
                            quest = quest,
                            onComplete = { onCompleteQuest(quest.title) },
                            onDelete = { onDeleteQuest(quest) }
                        )
                        if (index < activeQuests.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = DeepRose.copy(alpha = 0.05f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddQuestDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, diff ->
                onAddQuest(title, diff)
                showAddDialog = false
            },
            onClassify = onClassify
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 18.dp).background(DeepRose, CircleShape))
        Spacer(Modifier.width(10.dp))
        Icon(icon, null, tint = DeepRose, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Espresso)
    }
}

@Composable
private fun QuestItem(
    quest: QuestEntity,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val diffColor = when (quest.difficulty) {
        "HARD" -> Color(0xFFFF4444)
        "MEDIUM" -> DeepRose
        else -> Color(0xFF44AAFF)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                .clickable { onComplete() }
                .border(2.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quest.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Espresso
            )
            Surface(
                color = diffColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = quest.difficulty,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 9.sp,
                    color = diffColor,
                    fontWeight = FontWeight.Black
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddQuestDialog(
    onDismiss: () -> Unit, 
    onConfirm: (String, String) -> Unit,
    onClassify: suspend (String) -> String
) {
    var title by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var isClassifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Quest Baru") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { 
                        title = it 
                        difficulty = "" // Reset difficulty when title changes
                    },
                    label = { Text("Nama Tugas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Misal: Belajar coding") },
                    trailingIcon = {
                        if (title.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isClassifying = true
                                        difficulty = onClassify(title)
                                        isClassifying = false
                                    }
                                },
                                enabled = !isClassifying
                            ) {
                                if (isClassifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome, 
                                        contentDescription = "AI Classify", 
                                        tint = if (difficulty.isNotEmpty()) Color(0xFF4CAF50) else DeepRose
                                    )
                                }
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isClassifying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepRose)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Silica sedang menilai tugasmu... ♪",
                            fontSize = 12.sp,
                            color = DeepRose
                        )
                    }
                } else if (difficulty.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tingkat Kesulitan: ",
                            fontSize = 12.sp,
                            color = Espresso
                        )
                        Text(
                            difficulty,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = when (difficulty) {
                                "HARD" -> Color(0xFFFF4444)
                                "MEDIUM" -> DeepRose
                                else -> Color(0xFF44AAFF)
                            }
                        )
                    }
                } else {
                    Text(
                        "Klik ikon bintang agar Silica menilai tingkat kesulitannya. ♪",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (title.isNotBlank()) {
                        scope.launch {
                            var finalDiff = difficulty
                            if (finalDiff.isEmpty()) {
                                isClassifying = true
                                finalDiff = onClassify(title)
                                isClassifying = false
                            }
                            onConfirm(title, finalDiff)
                        }
                    }
                },
                enabled = title.isNotBlank() && !isClassifying
            ) { 
                Text(if (difficulty.isEmpty()) "Analisa & Tambah" else "Tambah") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isClassifying) { Text("Batal") }
        }
    )
}
