package com.silica.assistant.ui.guide

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.ui.theme.Espresso
import com.silica.assistant.ui.theme.DeepRose

private val CommandBg = Color(0xFF1A1A2E)
private val AccentLine = Color(0xFF8B4513)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            VoiceCommandsSection()
            FiturUtamaSection()
            SshConnectionSection()
            ThemeSection()
            Spacer(Modifier.height(16.dp))
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
private fun VoiceCommandsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Voice Commands", Icons.Filled.Mic, DeepRose)
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
                CommandItem("ssh_status", "Cek status koneksi SSH")

                CommandItem("ssh_connect", "Connect ke laptop (butuh host, user, password di form)")

                CommandItem("ssh_disconnect", "Putuskan koneksi SSH")

                CommandItem("laptop_info", "Tampilkan info laptop (uptime, RAM, disk)")

                CommandItem("volume [up/down/mute/set]", "Kontrol volume HP")

                CommandItem("brightness [up/down/set]", "Kontrol kecerahan layar")

                CommandItem("open [nama app]", "Buka app terinstall (WhatsApp, Telegram, game, dll)")

                CommandItem("silica", "Panggil asisten (nama opsional)")
            }
        }
    }
}

@Composable
private fun FiturUtamaSection() {
    val items = listOf(
        "Overlay Waifu" to "Floating character dengan ekspresi (IDLE, HAPPY, LISTENING). Drag untuk pindah, tap untuk listen, long-press untuk voice command.",
        "Voice Command" to "Perintah suara otomatis diproses. Hasil parsing tampil di layar utama.",
        "SSH Terminal" to "Akses terminal laptop via SSH. Support command linux + cd resolve lokal.",
        "SSH File Manager" to "Browse, upload, download file laptop via SFTP.",
        "Laptop Info" to "Monitor uptime, RAM, disk real-time (polling 3 detik).",
        "Buka Aplikasi" to "Buka app apa saja yang terinstall di HP via voice command."
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Fitur Utama", Icons.Filled.Star, Color(0xFFFFD700))
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
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
                ThemeRow("Dark Mode", "Yami theme dengan latar gelap")
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
