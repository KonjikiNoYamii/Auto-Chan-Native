package com.silica.assistant.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.llm.model.AchievementEntity
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementGalleryScreen(viewModel: AssistantViewModel, onBack: () -> Unit) {
    val achievements = viewModel.achievements
    val grouped = achievements.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Koleksi Achievement", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            grouped.forEach { (category, list) ->
                item {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepRose,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                items(list) { ach ->
                    AchievementCard(ach)
                }
            }
            
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun AchievementCard(achievement: AchievementEntity) {
    val tierColor = when (achievement.tier) {
        in 1..3 -> Color(0xFFCD7F32) // Bronze
        in 4..6 -> Color(0xFFC0C0C0) // Silver
        in 7..9 -> Color(0xFFFFD700) // Gold
        else -> Color(0xFFE5E4E2)    // Platinum
    }

    val isUnlocked = achievement.isUnlocked
    val opacity = if (isUnlocked) 1f else 0.4f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(if (isUnlocked) tierColor.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isUnlocked) Icons.Default.MilitaryTech else Icons.Default.Lock,
                    null,
                    tint = if (isUnlocked) tierColor else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    achievement.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isUnlocked) Espresso else Color.Gray
                )
                Text(
                    achievement.description,
                    fontSize = 12.sp,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                )
                
                if (!isUnlocked) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (achievement.currentValue.toFloat() / achievement.targetValue.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Gray.copy(alpha = 0.1f), CircleShape),
                        color = Color.Gray,
                        trackColor = Color.Transparent
                    )
                    Text(
                        "${achievement.currentValue} / ${achievement.targetValue}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
