package com.silica.assistant.ui.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AssistantViewModel, 
    onBack: () -> Unit, 
    onQuestHistory: () -> Unit,
    onAchievementGallery: () -> Unit
) {
    val moodManager: MoodManager = koinInject()
    val uiState = viewModel.uiState
    val profile = uiState.userProfile

    // Muat ulang profil saat layar dibuka untuk memastikan data ada
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DeepRose)
        }
        return
    }

    val level = profile.level
    val xp = profile.xp
    val nextXp = moodManager.getXpThresholdPublic(level)
    val progress = (xp.toFloat() / nextXp.toFloat()).coerceIn(0f, 1f)
    
    val relationshipStatus = when {
        profile.relationshipRoute == "LOVER" -> "Pasangan (Lover) (♡‿♡)"
        profile.relationshipRoute == "SOULMATE" -> "Sahabat Sejati (☆▽☆)"
        level > 30 -> "Teman Akrab"
        level > 15 -> "Kenalan Baik"
        else -> "Orang Asing"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Card Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DeepRose, DeepRose.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(4.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lv. $level",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = profile.userName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = relationshipStatus,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Section
            SectionHeader("Level Progress", Icons.Default.TrendingUp)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Experience (XP)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Espresso)
                        Text("$xp / $nextXp", fontSize = 14.sp, fontWeight = FontWeight.Black, color = DeepRose)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(CircleShape),
                        color = DeepRose,
                        trackColor = DeepRose.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Selesaikan quest untuk mendapatkan XP tambahan ♪",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Quest Authenticity Section
            SectionHeader("Autentisitas Quest", Icons.Default.VerifiedUser)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val verified = profile.verifiedQuestCount
                    val total = profile.totalQuestCount
                    val authenticityRatio = if (total > 0) (verified.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 1f
                    val percentage = (authenticityRatio * 100).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Terverifikasi vs Manual", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Espresso)
                        Text("$percentage%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (percentage > 70) Color(0xFF4CAF50) else DeepRose)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { authenticityRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(CircleShape),
                        color = if (percentage > 70) Color(0xFF4CAF50) else DeepRose,
                        trackColor = Color.Gray.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Total Quest: $total ($verified Terverifikasi)",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "Gunakan aplikasi yang relevan atau kirim bukti foto agar quest-mu terverifikasi oleh Silica. ♪",
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.8f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Section
            SectionHeader("Statistik Diri", Icons.Default.BarChart)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Streak",
                    value = "${profile.currentStreak} Hari",
                    color = Color(0xFFFF9800)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    label = "Longest",
                    value = "${profile.longestStreak} Hari",
                    color = Color(0xFFFFC107)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Achievement Summary
            SectionHeader("Pencapaian Terkini", Icons.Default.MilitaryTech)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val unlocked = viewModel.achievements.filter { it.isUnlocked }.takeLast(2)
                    if (unlocked.isEmpty()) {
                        Text(
                            "Belum ada achievement yang terbuka. Ayo semangat! ♪",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        unlocked.forEach { ach ->
                            AchievementItem(Icons.Default.MilitaryTech, ach.title, ach.description)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onQuestHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DeepRose),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Riwayat Quest", color = DeepRose, fontSize = 12.sp)
                }
                
                Button(
                    onClick = onAchievementGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Semua Achievement", color = Color.White, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                "Profil ini mencerminkan dedikasi dan perjalananmu bersama Silica ♪",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 18.dp).background(DeepRose, CircleShape))
        Spacer(Modifier.width(10.dp))
        Icon(icon, null, tint = DeepRose, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Espresso)
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Espresso)
        }
    }
}

@Composable
private fun AchievementItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Espresso)
            Text(desc, fontSize = 12.sp, color = Color.Gray)
        }
    }
}
