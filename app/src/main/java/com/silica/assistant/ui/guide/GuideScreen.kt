package com.silica.assistant.ui.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.theme.DeepRose
import kotlinx.coroutines.launch

private val CommandBg = Color(0xFF1A1A2E)

private data class TabData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabData("Perintah", Icons.Filled.Mic),
    TabData("Overlay", Icons.Filled.Visibility),
    TabData("Lainnya", Icons.Filled.Star),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panduan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = DeepRose,
                divider = { HorizontalDivider(color = DeepRose.copy(alpha = 0.3f)) }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title, fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> CommandsTab()
                    1 -> OverlayTab()
                    2 -> OthersTab()
                }
            }
        }
    }
}

@Composable
private fun CommandsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TipItem("Typo dikit masih kebaca, misal \"spotifi\" \u2192 spotify")
                TipItem("Gak perlu hafal semua, tinggal bilang aja apa yang mau dilakukan")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                CommandItem("ssh_status", "Cek status koneksi SSH")
                CommandItem("ssh_connect", "Connect ke laptop")
                CommandItem("ssh_disconnect", "Putuskan koneksi SSH")
                CommandItem("laptop_info", "Tampilkan info laptop (uptime, RAM, disk)")
                CommandItem("volume [up/down/mute]", "Kontrol volume HP")
                CommandItem("brightness [up/down]", "Kontrol kecerahan layar")
                CommandItem("buka [nama app]", "Buka app apapun (WhatsApp, Telegram, game)")
                CommandItem("cari / google [query]", "Cari sesuatu di Google")
                CommandItem("play musik / next song", "Kontrol media player")
                CommandItem("tier list genshin / ml / valorant", "Info tier list game")
                CommandItem("silica ...", "Wake word (bisa dimatikan di pengaturan)")
            }
        }
    }
}

@Composable
private fun OverlayTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OverlayStep(
                    icon = Icons.Filled.Visibility,
                    title = "Aktifkan Overlay",
                    desc = "Tekan tombol Start pada panel Overlay di halaman utama."
                )
                OverlayStep(
                    icon = Icons.Filled.TouchApp,
                    title = "Tap untuk Voice",
                    desc = "Tap sekali pada karakter waifu untuk memulai voice command."
                )
                OverlayStep(
                    icon = Icons.Filled.OpenWith,
                    title = "Drag untuk Pindah",
                    desc = "Geser karakter ke posisi mana pun di layar."
                )
                OverlayStep(
                    icon = Icons.Filled.Mic,
                    title = "Long Press untuk Voice",
                    desc = "Tekan dan tahan karakter untuk langsung voice command."
                )
                OverlayStep(
                    icon = Icons.Filled.Face,
                    title = "Ekspresi Waifu",
                    desc = "Karakter berubah ekspresi sesuai keadaan: RELAX, TALK, LISTEN."
                )
                OverlayStep(
                    icon = Icons.Filled.ChatBubble,
                    title = "Bubble Chat",
                    desc = "Waifu menampilkan gelembung chat sebagai respons atas perintah Anda."
                )
            }
        }
    }
}

@Composable
private fun OthersTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FiturUtamaSection()
        SshConnectionSection()
        ThemeSection()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OverlayStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DeepRose.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DeepRose, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = DeepRose) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color
        )
    }
}

@Composable
private fun CommandItem(command: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text("> ", color = Color(0xFF00FF88), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Column {
            Surface(
                color = CommandBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun FiturUtamaSection() {
    val items = listOf(
        "Voice Command" to "Perintah suara otomatis diproses. Hasil parsing tampil di layar utama.",
        "SSH Terminal" to "Akses terminal laptop via SSH.",
        "SSH File Manager" to "Browse, upload, download file laptop via SFTP.",
        "Laptop Info" to "Monitor uptime, RAM, disk real-time.",
        "Buka Aplikasi" to "Buka app apa saja yang terinstall di HP. Support label + alias (wa, ig, fb). Bahasa Indonesia & English."
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Fitur", Icons.Filled.Star, Color(0xFFFFD700))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEach { (title, desc) ->
                    FeatureItem(title, desc)
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(DeepRose.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Espresso
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SshConnectionSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("SSH Connection", Icons.Filled.Lan, Color(0xFF00CCFF))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepItem(1, "Isi Host, Port (22), Username, Password")
                StepItem(2, "Tekan Connect")
                StepItem(3, "Gunakan Terminal untuk eksekusi command")
                StepItem(4, "Gunakan Files untuk upload/download")
                StepItem(5, "Tombol LinkOff \u2192 disconnect")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TipItem("Back ke layar utama tetap connect")
                TipItem("Session auto-terdeteksi jika koneksi putus")
            }
        }
    }
}

@Composable
private fun StepItem(step: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = DeepRose,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("$step", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun TipItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\u2726 ", color = Color(0xFF00FF88), fontSize = 14.sp)
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Tema", Icons.Filled.Palette, Color(0xFFE8919A))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeRow("Aksen", "DeepRose (merah) + Espresso (coklat)")
                ThemeRow("Header", "Gambar anime, sapaan berdasarkan waktu")
                ThemeRow("Status Bar", "Menampilkan status SSH dan info lainnya")
            }
        }
    }
}

@Composable
private fun ThemeRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Espresso,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
