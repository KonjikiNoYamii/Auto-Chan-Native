package com.silica.assistant.ui.tutorial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.DeepRose
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pages(context).size })
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                OnboardingPage(pageData = pages(context)[page], context = context)
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages(context).indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == i) DeepRose else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                if (pagerState.currentPage == pages(context).lastIndex) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                    ) {
                        Text("MULAI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                    ) {
                        Text("LANJUT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(pageData: PageData, context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(pageData.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = pageData.icon, contentDescription = null, tint = pageData.color, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(text = pageData.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(text = pageData.description, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp)

        if (pageData.actionLabel != null && pageData.action != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = {
                    try { pageData.action.invoke() } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(pageData.actionLabel, fontSize = 13.sp)
            }
        }
    }
}

private data class PageData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

private fun pages(context: Context): List<PageData> {
    val pkg = "package:${context.packageName}"
    return listOf(
        PageData(
            icon = Icons.Default.Face,
            title = "Selamat Datang!",
            description = "Silica Assistant adalah asisten pribadimu dengan karakter waifu interaktif.\n\nGunakan suara, overlay, atau gamepad untuk mengontrol HP dan laptopmu.",
            color = DeepRose
        ),
        PageData(
            icon = Icons.Default.Visibility,
            title = "Izin Overlay",
            description = "Aktifkan izin tampilkan di atas aplikasi lain agar waifu bisa muncul mengambang di layar HP-mu.",
            color = Color(0xFF7C4DFF),
            actionLabel = "Buka Pengaturan Overlay",
            action = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg))) }
        ),
        PageData(
            icon = Icons.Default.Description,
            title = "Aksesibilitas",
            description = "Aktifkan Silica di menu Aksesibilitas agar bisa membaca layar, klik otomatis, scroll, dan kembali.",
            color = Color(0xFF00BCD4),
            actionLabel = "Buka Aksesibilitas",
            action = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ),
        PageData(
            icon = Icons.Default.Mic,
            title = "Mikrofon & Notifikasi",
            description = "Silica butuh izin mikrofon untuk voice command dan notifikasi untuk tetap berjalan di latar belakang.",
            color = Color(0xFFFF9800),
            actionLabel = "Buka Izin Aplikasi",
            action = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse(pkg))) }
        ),
        PageData(
            icon = Icons.Default.CheckCircle,
            title = "Siap Digunakan!",
            description = "Kamu sudah siap menggunakan Silica Assistant.\n\nCoba mulai overlay atau coba fitur voice command!",
            color = Color(0xFF4CAF50)
        )
    )
}
