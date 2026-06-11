package com.silica.assistant.ui.affinity

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.silica.assistant.R
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.config.AssistantConfig
import com.silica.assistant.core.overlay.OverlayEventBus
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel
import androidx.compose.ui.graphics.painter.BitmapPainter
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffinityScreen(viewModel: AssistantViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val moodManager: com.silica.assistant.core.llm.MoodManager = org.koin.compose.koinInject()
    val uiState = viewModel.uiState
    val profile = uiState.userProfile
    val points = profile?.affinityPoints ?: 0
    var showGiftDialog by remember { mutableStateOf(false) }

    val assistantName = AssistantConfig.assistantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    if (showGiftDialog) {
        val inventory = viewModel.inventory
        GiftSelectionDialog(
            inventory = inventory,
            onDismiss = { showGiftDialog = false },
            onGiftSelected = { gift ->
                showGiftDialog = false
                scope.launch {
                    val (success, response) = moodManager.giveGift(gift)
                    if (success) {
                        moodManager.removeItemFromInventory(gift)
                        viewModel.loadInventory()
                    }
                    OverlayEventBus.onBubble?.invoke(response)
                    viewModel.loadProfile()
                }
            }
        )
    }

    val relationshipLevel = when {
        points > 100 -> "Sangat Menyayangi (Loving)"
        points > 50 -> "Teman Dekat (Friendly)"
        points < -50 -> "Dingin / Kesal (Cold)"
        else -> "Teman Biasa (Neutral)"
    }

    val levelColor = when {
        points > 50 -> DeepRose
        points < -50 -> Color.Gray
        else -> Espresso
    }

    val progress = ((points + 100).toFloat() / 300f).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Affinity Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val assistantIcon = remember(context) { CustomAssetManager.loadImageBitmap(context, CustomAssetManager.AssetType.CHAT_ICON) }

            // Waifu Image Section
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(4.dp, levelColor, CircleShape)
                        .background(Color.White)
                ) {
                    if (assistantIcon != null) {
                        Image(
                            painter = BitmapPainter(assistantIcon),
                            contentDescription = assistantName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.iconchat),
                            contentDescription = assistantName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = relationshipLevel,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = levelColor
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showGiftDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = DeepRose),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Beri Hadiah", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            // Affinity Bar Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Poin Afinitas", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Espresso)
                        Text("$points / 200", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = levelColor)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(CircleShape),
                        color = levelColor,
                        trackColor = levelColor.copy(alpha = 0.1f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Info Section matching GuideScreen style
            SectionHeader("Informasi", Icons.Default.Info)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AffinityTipItem(Icons.Default.Favorite, "Cara meningkatkan", "Bicaralah dengan sopan, ucapkan terima kasih, atau sapa $assistantName setiap hari.")
                    AffinityTipItem(Icons.Default.SentimentDissatisfied, "Penyebab turun", "Menggunakan kata-kata kasar atau mengabaikannya dalam waktu lama.")
                    AffinityTipItem(Icons.Default.SentimentVerySatisfied, "Keuntungan", "$assistantName akan memberikan panggilan spesial dan respon yang lebih perhatian.")
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Text(
                text = "Semakin tinggi hubunganmu, semakin banyak fitur rahasia yang akan terbuka ♪",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp),
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 20.dp).background(DeepRose, CircleShape))
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, tint = DeepRose, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Espresso)
    }
}

@Composable
private fun AffinityTipItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(32.dp).background(DeepRose.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = DeepRose, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Espresso)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun GiftSelectionDialog(
    inventory: List<String>,
    onDismiss: () -> Unit, 
    onGiftSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Beri Hadiah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Espresso)
                Text("Pilih hadiah dari inventory-mu ♪", fontSize = 12.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(20.dp))
                
                if (inventory.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(48.dp), tint = Color.Gray.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Inventory kosong.\nSelesaikan quest untuk dapat item!",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    val giftCounts = inventory.groupingBy { it }.eachCount()
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        giftCounts.forEach { (giftName, count) ->
                            val giftIcon = when {
                                giftName.contains("Taiyaki") -> "🐟"
                                giftName.contains("Cokelat") -> "🍫"
                                giftName.contains("Kopi") -> "☕"
                                giftName.contains("Permen") -> "🍬"
                                giftName.contains("Teh") -> "🍵"
                                else -> "🎁"
                            }
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onGiftSelected(giftName) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DeepRose.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(giftIcon, fontSize = 20.sp)
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(giftName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Espresso)
                                        Text("$count Tersedia", fontSize = 11.sp, color = DeepRose, fontWeight = FontWeight.Medium)
                                    }
                                    Icon(Icons.Default.ArrowForward, null, tint = Color.Gray.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text("Tutup", fontWeight = FontWeight.Bold, color = Color.Gray) 
                }
            }
        }
    }
}
