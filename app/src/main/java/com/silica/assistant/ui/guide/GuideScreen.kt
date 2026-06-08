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

// ==============================
// TAB 1: PERINTAH SUARA
// ==============================
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
                TipItem("Wake word \"Silica\" bisa dipakai atau dimatikan di pengaturan")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Aplikasi")
                CommandItem("buka spotify / spotify / play lagu", "Buka Spotify")
                CommandItem("buka youtube / youtube / video", "Buka YouTube")
                CommandItem("buka browser / chrome / internet", "Buka browser")
                CommandItem("buka pengaturan / settings", "Buka Settings HP")
                CommandItem("buka [nama app]", "Buka app apapun (wa, ig, telegram, game, dll)")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Media & Suara")
                CommandItem("play musik / pause musik / nyalain musik", "Play/Pause musik")
                CommandItem("next song / lagu berikutnya / skip", "Lagu berikutnya")
                CommandItem("previous song / lagu sebelumnya", "Lagu sebelumnya")
                CommandItem("volume naik / besarkan suara", "Volume naik")
                CommandItem("volume turun / kecilkan suara", "Volume turun")
                CommandItem("mute / matikan suara", "Mute volume")
                CommandItem("volume maksimal / full volume", "Volume maksimal")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Layar")
                CommandItem("brightness naik / cerahkan layar", "Kecerahan naik")
                CommandItem("brightness turun / gelapkan layar", "Kecerahan turun")
                CommandItem("brightness maksimal / layar paling terang", "Kecerahan maksimal")
                CommandItem("brightness minimum / layar paling redup", "Kecerahan minimum")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Layar (Aksesibilitas)")
                CommandItem("ini apa / apa ini / deskripsi layar", "AI deskripsikan isi layar (text + screenshot)")
                CommandItem("klik [teks] / tekan [teks]", "Cari & klik elemen yang berisi teks tertentu")
                CommandItem("scroll ke bawah / scroll down", "Gulir layar ke bawah")
                CommandItem("scroll ke atas / scroll up", "Gulir layar ke atas")
                CommandItem("kembali / back / mundur", "Tombol navigasi kembali")
                CommandItem("beranda / home", "Kembali ke layar utama")
                CommandItem("notifikasi / notifications", "Buka panel notifikasi")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Game Mode")
                CommandItem("mode game / game mode / aktifkan mode game", "Aktifkan mode game (waifu transparan)")
                CommandItem("stop game mode / matikan mode game", "Nonaktifkan mode game")
                CommandItem("ini game mode ku / set game mode", "Set app sekarang sbg game mode auto")
                CommandItem("hapus game mode / reset game mode", "Hapus app game mode")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("SSH & Laptop")
                CommandItem("status ssh / cek ssh / koneksi laptop", "Cek status koneksi SSH")
                CommandItem("konek ssh / hubungkan laptop", "Buka layar koneksi SSH")
                CommandItem("putuskan ssh / disconnect ssh", "Putuskan koneksi SSH")
                CommandItem("info laptop / status laptop", "Tampilkan info laptop (uptime, RAM, disk)")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Chat & Lainnya")
                CommandItem("chat / chat ai / tanya", "Buka chat AI (ngobrol dengan Yami)")
                CommandItem("cari [query] / google [query]", "Cari sesuatu di Google")
                CommandItem("tier list genshin / ml / valorant", "Info tier list karakter game")
                CommandItem("overlay / waifu / start overlay", "Tampilkan overlay waifu")
                CommandItem("tutup overlay / stop waifu", "Sembunyikan overlay waifu")
            }
        }
    }
}

// ==============================
// TAB 2: OVERLAY
// ==============================
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
                    desc = "Tekan tombol Start di halaman utama, atau bilang \"overlay\"."
                )
                OverlayStep(
                    icon = Icons.Filled.TouchApp,
                    title = "Tap untuk Voice",
                    desc = "Tap waifu untuk toggle listening ON/OFF."
                )
                OverlayStep(
                    icon = Icons.Filled.Mic,
                    title = "Long Press untuk Voice",
                    desc = "Tekan & tahan waifu (600ms) untuk langsung voice command."
                )
                OverlayStep(
                    icon = Icons.Filled.OpenWith,
                    title = "Drag & Snap",
                    desc = "Geser waifu ke posisi mana pun. Lepas di dekat tepi \u2192 snap otomatis."
                )
                OverlayStep(
                    icon = Icons.Filled.Face,
                    title = "Ekspresi Waifu",
                    desc = "Relax (diam), Talk (bicara), Listen (mendengar) — berganti otomatis."
                )
                OverlayStep(
                    icon = Icons.Filled.ChatBubble,
                    title = "Bubble Chat",
                    desc = "Gelembung teks sebagai respons. Posisi otomatis menyesuaikan."
                )
                OverlayStep(
                    icon = Icons.Filled.PhotoCamera,
                    title = "Screen Capture",
                    desc = "Untuk fitur \"ini apa\" dan auto-comment, butuh izin screen capture."
                )
                OverlayStep(
                    icon = Icons.Filled.Description,
                    title = "Aksesibilitas",
                    desc = "Untuk klik, scroll, back, baca teks layar — aktifkan Silica di Settings > Aksesibilitas."
                )
                OverlayStep(
                    icon = Icons.Filled.Gamepad,
                    title = "Game Mode",
                    desc = "Otomatis aktif saat buka game. Waifu transparan (0.55) & pindah ke atas."
                )
                OverlayStep(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Auto Comment",
                    desc = "Waifu komen otomatis tiap ganti app (via AI). Di game tiap 20-90 detik."
                )
                OverlayStep(
                    icon = Icons.Filled.Star,
                    title = "Random Quotes",
                    desc = "Waifu ngomong random quote saat nganggur. Gaya tsundere khas Yami."
                )
            }
        }
    }
}

// ==============================
// TAB 3: LAINNYA
// ==============================
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
        Spacer(Modifier.height(8.dp))
        SshConnectionSection()
        Spacer(Modifier.height(8.dp))
        PermissionsSection()
        Spacer(Modifier.height(8.dp))
        ThemeSection()
        Spacer(Modifier.height(16.dp))
    }
}

// ==============================
// REUSABLE COMPONENTS
// ==============================
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = DeepRose,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun FiturUtamaSection() {
    val items = listOf(
        "AI Chat (Yami)" to "Ngobrol dengan Konjiki no Yami lewat teks. Dual provider: OpenRouter (utama) + Gemini (cadangan).",
        "Voice Command" to "Perintah suara otomatis diproses. Support bahasa Indonesia + Inggris + typo tolerance.",
        "Screen-Aware" to "Waifu bisa baca teks layar (aksesibilitas) + lihat screenshot (media projection). Deskripsi otomatis tiap 2 menit.",
        "Klik & Scroll" to "Klik elemen berdasarkan teks, scroll, back, home — semua via AccessibilityService.",
        "SSH Terminal" to "Akses terminal laptop via SSH. Juga ada file manager SFTP + monitor live.",
        "Game Mode" to "Auto-detect game. Waifu transparan di atas layar, komentar otomatis selama main.",
        "Knowledge Base" to "Tanya tier list & karakter terbaik Genshin Impact, Mobile Legends, Valorant.",
        "Buka App Pintar" to "Buka app apa pun dengan nama/shorthand (wa=WhatsApp, ig=Instagram, dll). Fallback Google search.",
        "Command History" to "Semua perintah tercatat & bisa dilihat di layar utama.",
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
                StepItem(4, "Gunakan Files untuk upload/download file")
                StepItem(5, "Tombol LinkOff \u2192 disconnect")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TipItem("Back ke layar utama tetap connect")
                TipItem("Session auto-terdeteksi jika koneksi putus")
                TipItem("SSH Key authentication juga didukung (generasi RSA 2048)")
            }
        }
    }
}

@Composable
private fun PermissionsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Izin Dibutuhkan", Icons.Filled.Security, Color(0xFFFF6B6B))
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
                PermItem("Overlay (SYSTEM_ALERT_WINDOW)", "Menampilkan waifu mengambang")
                PermItem("Mikrofon (RECORD_AUDIO)", "Voice command")
                PermItem("Akses Penggunaan (Usage Stats)", "Deteksi game otomatis")
                PermItem("Screen Capture (MediaProjection)", "Fitur ini apa & deskripsi layar")
                PermItem("Aksesibilitas (AccessibilityService)", "Baca teks, klik, scroll, back")
                PermItem("Ubah Setelan (WRITE_SETTINGS)", "Kontrol brightness")
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            TipItem("Aktifkan Aksesibilitas: Setelan > Aksesibilitas > Silica Assistant")
            TipItem("Aktifkan Izin Lain: Akan muncul otomatis saat fitur dipakai pertama")
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
private fun PermItem(label: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00FF88),
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Tema & Kustomisasi", Icons.Filled.Palette, Color(0xFFE8919A))
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
                ThemeRow("Warna aksen", "DeepRose (merah) + Espresso (coklat)")
                ThemeRow("Header", "Gambar anime + sapaan berdasarkan waktu")
                ThemeRow("Status Bar", "Status SSH, koneksi, dan info lainnya")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TipItem("Ganti sprite waifu (idle, happy, listening, game) di halaman Customize")
                TipItem("Ganti pop sound & header gambar juga bisa")
                TipItem("Sapaan bisa dikustom per waktu (pagi/siang/sore/malam)")
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
