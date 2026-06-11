package com.silica.assistant.ui.affinity

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.R
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel

import com.silica.assistant.core.config.AssistantConfig
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffinityScreen(viewModel: AssistantViewModel, onBack: () -> Unit) {
    val uiState = viewModel.uiState
    val profile = uiState.userProfile
    val points = profile?.affinityPoints ?: 0
    val assistantName = AssistantConfig.assistantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

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
                title = { Text("Hubungan dengan $assistantName", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waifu Image with dynamic border based on affinity
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(4.dp, levelColor, CircleShape)
                    .background(Color.White)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.yami_smile), // Fallback image
                    contentDescription = assistantName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = relationshipLevel,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = levelColor
            )

            Spacer(Modifier.height(8.dp))

            // Affinity Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Poin Afinitas", fontSize = 12.sp, color = Color.Gray)
                    Text("$points / 200", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = levelColor,
                    trackColor = levelColor.copy(alpha = 0.2f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // Info Cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow(Icons.Default.Favorite, "Cara meningkatkan", "Bicaralah dengan sopan, ucapkan terima kasih, atau sapa $assistantName setiap hari.")
                    InfoRow(Icons.Default.SentimentDissatisfied, "Penyebab turun", "Menggunakan kata-kata kasar atau mengabaikannya dalam waktu lama.")
                    InfoRow(Icons.Default.SentimentVerySatisfied, "Keuntungan", "$assistantName akan memanggilmu 'Tuan' dan memberikan respon yang lebih perhatian.")
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Semakin tinggi hubunganmu, semakin banyak fitur rahasia yang akan terbuka ★",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 20.dp),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = DeepRose, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
