package com.silica.assistant.ui.guide

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.theme.DeepRose
import kotlinx.coroutines.launch
import java.util.Calendar

private val CommandBg = Color(0xFF1A1A2E)

private data class TabData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabData("Perintah", Icons.Filled.Mic),
    TabData("Overlay", Icons.Filled.Visibility),
    TabData("Gamepad", Icons.Filled.Gamepad),
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
                title = { Text("Panduan Lengkap Silica", fontWeight = FontWeight.Bold) },
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
                        text = { Text(tab.title, fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                    2 -> GamepadTab()
                    3 -> OthersTab()
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
                TipItem("Wake word \"Silica\" bisa dipakai atau dimatikan di pengaturan")
                TipItem("Gak perlu hafal semua, Yami cukup pintar untuk paham maksudmu")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Smart Remote (AI Vision)")
                CommandItem("klik tombol [nama] di laptop", "AI 'melihat' layar laptop & klik tombol itu")
                CommandItem("tekan tombol [nama] laptop", "Sama seperti klik tombol (via AI vision)")
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Aplikasi & Dasar")
                CommandItem("buka [nama app]", "Buka WhatsApp, YouTube, Game, dll")
                CommandItem("buka pengaturan", "Buka Settings HP")
                CommandItem("cari [query] / google [query]", "Cari info di internet")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Laptop (SSH & Remote)")
                CommandItem("laptop ketik [teks]", "Ketik teks langsung di laptop")
                CommandItem("laptop klik kiri / kanan", "Kontrol mouse laptop")
                CommandItem("laptop klik di [x] [y]", "Klik pada koordinat spesifik")
                CommandItem("laptop tekan tombol [tombol]", "Tekan Enter, Space, BackSpace, dll")
                CommandItem("info laptop / status ssh", "Cek kondisi koneksi & laptop")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Media & Hiburan")
                CommandItem("putar lagu [judul]", "Putar musik otomatis di Spotify/YouTube")
                CommandItem("play / pause / next / skip", "Kontrol pemutar musik")
                CommandItem("lagu sebelumnya / previous", "Kembali ke lagu sebelumnya")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Layar & Sistem")
                CommandItem("brightness naik / turun", "Atur kecerahan layar HP")
                CommandItem("brightness maksimal / minimum", "Set terang/redup maksimal")
                CommandItem("ini apa / deskripsi layar", "Yami jelaskan isi layar HP-mu")
                CommandItem("klik [teks] / scroll down", "Kontrol layar HP via suara")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Hubungan & Obrolan")
                CommandItem("terima kasih / makasih", "Naikkan afinitas (Yami jadi ramah)")
                CommandItem("bodoh / jelek / benci", "Turunkan afinitas (Yami jadi dingin)")
                CommandItem("chat / tanya / apa kabar", "Ngobrol santai dengan Yami")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Game Mode")
                CommandItem("mode game / game mode", "Aktifkan waifu transparan")
                CommandItem("matikan mode game", "Kembali ke mode normal")
                CommandItem("set game mode / hapus game mode", "Atur auto-detect app")
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
                OverlayStep(Icons.Filled.Visibility, "Aktifkan Overlay", "Tekan Start di Home atau bilang \"overlay\".")
                OverlayStep(Icons.Filled.TouchApp, "Tap Waifu", "Toggle mode mendengarkan (On/Off).")
                OverlayStep(Icons.Filled.Mic, "Tahan Waifu", "Tekan lama (600ms) untuk bicara langsung.")
                OverlayStep(Icons.Filled.OpenWith, "Geser Waifu", "Pindahkan waifu. Snap otomatis ke tepi.")
                OverlayStep(Icons.Filled.Gamepad, "Game Mode", "Transparan (0.55) & posisi atas saat main game.")
                OverlayStep(Icons.Filled.AutoAwesome, "Komentar AI", "Silica komen tiap 15 menit tentang app kamu.")
                OverlayStep(Icons.Filled.Favorite, "Sistem Afinitas", "Bicara sopan = Ramah. Bicara kasar = Ketus.")
                OverlayStep(Icons.Filled.Psychology, "Memori Chat", "Semua riwayat obrolan tersimpan permanen.")
                OverlayStep(Icons.Filled.Description, "Aksesibilitas", "Aktifkan Silica untuk kontrol layar penuh.")
            }
        }
    }
}

@Composable
private fun GamepadTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader("Kontrol Gamepad", Icons.Filled.Gamepad, DeepRose)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GamepadFeatureItem(Icons.Filled.AdsClick, "Joystick Analog", "Kontrol WASD presisi untuk bergerak.")
                GamepadFeatureItem(Icons.Filled.PanToolAlt, "Touchpad", "Geser kamera & Tap untuk klik mouse.")
                GamepadFeatureItem(Icons.Filled.Image, "Background", "Ganti latar belakang dengan gambar galeri.")
                GamepadFeatureItem(Icons.Filled.Edit, "Edit Mode", "Atur posisi semua tombol sesukamu.")
            }
        }

        SectionHeader("Cara Edit", Icons.Filled.Settings, Color(0xFFFFD700))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepItem(1, "Klik ikon Pensil di pojok kanan atas Gamepad.")
                StepItem(2, "Tahan & Geser tombol ke posisi yang diinginkan.")
                StepItem(3, "Gunakan ikon + untuk menambah tombol keyboard.")
                StepItem(4, "Klik ikon Gambar untuk memilih wallpaper.")
                StepItem(5, "Klik ikon Save untuk mengunci posisi.")
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
        FiturLengkapSection()
        Spacer(Modifier.height(8.dp))
        SshDetailSection()
        Spacer(Modifier.height(8.dp))
        ThemeDetailSection()
        Spacer(Modifier.height(8.dp))
        PermissionsFullSection()
        Spacer(Modifier.height(16.dp))
    }
}

// ==============================
// DETAILED COMPONENTS
// ==============================
@Composable
private fun FiturLengkapSection() {
    val items = listOf(
        "AI Chat (Yami)" to "Ngobrol natural. Local server + Gemini API fallback.",
        "Voice Typing Remote" to "Ketik di laptop lewat suara dari mana saja.",
        "Smart Vision Click" to "AI deteksi tombol di laptop & klik otomatis.",
        "SFTP File Manager" to "Upload/Download file laptop langsung dari HP.",
        "Auto Game Mode" to "Waifu otomatis menyesuaikan diri saat kamu gaming.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Fitur Utama", Icons.Filled.Star, Color(0xFFFFD700))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (title, desc) -> FeatureItem(title, desc) }
            }
        }
    }
}

@Composable
private fun SshDetailSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Koneksi SSH", Icons.Filled.Lan, Color(0xFF00CCFF))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepItem(1, "Isi IP Laptop, Username, dan Password.")
                StepItem(2, "Klik Connect. Status hijau = Terhubung.")
                StepItem(3, "Yami siap akses laptopmu via Suara/Gamepad.")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TipItem("Gunakan SSH Key untuk login tanpa password.")
            }
        }
    }
}

@Composable
private fun ThemeDetailSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Kustomisasi", Icons.Filled.Palette, Color(0xFFE8919A))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TipItem("Ganti sprite waifu (Idle/Happy/Listen) di Customize.")
                TipItem("Ganti Header gambar & sapaan kustom sesukamu.")
                TipItem("Semua suara (Pop/Level Up) juga bisa diganti.")
            }
        }
    }
}

@Composable
private fun PermissionsFullSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Izin Aplikasi", Icons.Filled.Security, Color(0xFFFF6B6B))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PermButton("Overlay", "Tampilkan waifu di atas app lain") { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                PermButton("Aksesibilitas", "Klik, Scroll, & Baca layar HP") { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                PermButton("Mikrofon", "Mendengarkan perintah suaramu") { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                PermButton("Ubah Setelan", "Kontrol Brightness & Volume") { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            }
        }
    }
}

// ==============================
// BASE REUSABLE COMPONENTS
// ==============================
@Composable
private fun GamepadFeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = DeepRose, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Espresso)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun OverlayStep(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(DeepRose.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = DeepRose, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = DeepRose) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
    }
}

@Composable
private fun CommandItem(command: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("> ", color = Color(0xFF00FF88), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Column {
            Surface(color = CommandBg, shape = RoundedCornerShape(4.dp)) {
                Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFFFD700), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = DeepRose, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun FeatureItem(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).background(DeepRose, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Espresso)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun StepItem(step: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = DeepRose, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("$step", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
private fun TipItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\u2726 ", color = Color(0xFF00FF88), fontSize = 12.sp)
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermButton(label: String, desc: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00FF88), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
