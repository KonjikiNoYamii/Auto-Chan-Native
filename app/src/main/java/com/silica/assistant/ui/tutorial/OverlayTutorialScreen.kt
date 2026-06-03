package com.silica.assistant.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.DeepRose
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayTutorialScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Tutorial Overlay", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DeepRose,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            TutorialStep(
                icon = steps[page].icon,
                title = steps[page].title,
                description = steps[page].description,
                color = steps[page].color
            )
        }

        // Bottom: dots + button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) DeepRose
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (pagerState.currentPage == steps.lastIndex) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) {
                    Text(
                        "MULAI OVERLAY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) {
                    Text(
                        "LANJUT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialStep(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = description,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

private data class TutorialStepData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

private val steps = listOf(
    TutorialStepData(
        icon = Icons.Filled.Visibility,
        title = "Aktifkan Overlay",
        description = "Tekan tombol Start pada panel Overlay di halaman utama. Sebuah karakter waifu akan muncul mengambang di layar HP Anda.",
        color = DeepRose
    ),
    TutorialStepData(
        icon = Icons.Filled.TouchApp,
        title = "Tap untuk Voice",
        description = "Tap sekali pada karakter untuk memulai voice command. Waifu akan mendengarkan perintah Anda setelah Anda tap.",
        color = Color(0xFF7C4DFF)
    ),
    TutorialStepData(
        icon = Icons.Filled.OpenWith,
        title = "Drag untuk Pindah",
        description = "Geser (drag) karakter ke posisi mana pun di layar. Bebas dipindahkan agar tidak mengganggu tampilan.",
        color = Color(0xFF00BCD4)
    ),
    TutorialStepData(
        icon = Icons.Filled.Mic,
        title = "Long Press untuk Voice",
        description = "Tekan dan tahan karakter selama beberapa saat (long press) untuk langsung mengaktifkan mode voice command.",
        color = Color(0xFFFF9800)
    ),
    TutorialStepData(
        icon = Icons.Filled.Face,
        title = "Ekspresi Waifu",
        description = "Karakter akan berubah ekspresi: tersenyum (IDLE), bahagia (HAPPY), atau mendengarkan (LISTENING) sesuai dengan apa yang sedang terjadi.",
        color = Color(0xFFE91E63)
    ),
    TutorialStepData(
        icon = Icons.Filled.ChatBubble,
        title = "Bubble Chat",
        description = "Waifu akan menampilkan gelembung chat sebagai respons atas perintah atau aksi yang Anda lakukan. Keren, kan?",
        color = Color(0xFF4CAF50)
    )
)
