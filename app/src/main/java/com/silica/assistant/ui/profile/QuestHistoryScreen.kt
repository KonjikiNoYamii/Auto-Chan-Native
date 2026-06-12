package com.silica.assistant.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestHistoryScreen(viewModel: AssistantViewModel, onBack: () -> Unit) {
    val completedQuests = viewModel.completedQuests
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
    // Launcher for image picker
    var selectedQuestTitle by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val title = selectedQuestTitle ?: return@let
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                viewModel.verifyQuestWithPhoto(title, bytes) { success, message ->
                    com.silica.assistant.core.overlay.OverlayEventBus.onBubble?.invoke(message)
                }
            }
        }
    }

    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Quest", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (completedQuests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada quest yang diselesaikan.", color = Color.Gray)
            }
        } else {
            val groupedQuests = completedQuests.groupBy { 
                val date = Date(it.completedAt ?: it.createdAt)
                SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(date)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedQuests.forEach { (date, quests) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleSmall,
                            color = DeepRose,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(quests) { quest ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (quest.isEligible) Icons.Default.Verified else Icons.Default.History, 
                                    null, 
                                    tint = if (quest.isEligible) Color(0xFF4CAF50) else Color.Gray, 
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(quest.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Espresso)
                                        if (quest.isEligible) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Text(
                                        text = if (quest.isEligible) "Terverifikasi Otomatis/Foto" else "Selesai Manual (Belum Terverifikasi)",
                                        fontSize = 11.sp,
                                        color = if (quest.isEligible) Color(0xFF4CAF50) else Color.Gray
                                    )
                                }
                                
                                if (!quest.isEligible) {
                                    IconButton(
                                        onClick = { 
                                            selectedQuestTitle = quest.title
                                            imagePickerLauncher.launch("image/*") 
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.FileUpload, "Verify with Photo", tint = DeepRose, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    Text(quest.difficulty, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepRose)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
