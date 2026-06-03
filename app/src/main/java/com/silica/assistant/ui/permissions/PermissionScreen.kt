package com.silica.assistant.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.silica.assistant.ui.theme.DeepRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var recordGranted by remember {
        mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
        )
    }
    var notifyGranted by remember {
        mutableStateOf(
                Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var settingsGranted by remember { mutableStateOf(Settings.System.canWrite(context)) }

    // Cek status izin secara dinamis ketika user kembali dari settings Android
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                recordGranted =
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                notifyGranted =
                        Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                overlayGranted = Settings.canDrawOverlays(context)
                settingsGranted = Settings.System.canWrite(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recordLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                recordGranted = it
            }

    val notifyLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notifyGranted = it
            }

    // Menghitung apakah semua izin krusial sudah diaktifkan
    val allPermissionsGranted = recordGranted && notifyGranted && overlayGranted && settingsGranted

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
                title = { Text("Izin Aplikasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                        TopAppBarDefaults.topAppBarColors(
                                containerColor = DeepRose,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
        )

        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "Aktifkan izin berikut agar semua fitur Waifu berfungsi dengan normal:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 1. MIKROFON
            PermCard(
                    icon = Icons.Filled.Mic,
                    title = "Mikrofon / Rekam Suara",
                    desc =
                            "Dibutuhkan agar Waifu bisa mendengar perintah suara (voice command) Anda.",
                    isGranted = recordGranted,
                    onRequest = { recordLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            // 2. NOTIFIKASI
            if (Build.VERSION.SDK_INT >= 33) {
                PermCard(
                        icon = Icons.Filled.Notifications,
                        title = "Notifikasi Sistem",
                        desc =
                                "Dibutuhkan untuk menjaga layanan kontrol suara tetap berjalan di latar belakang.",
                        isGranted = notifyGranted,
                        onRequest = {
                            notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                )
            }

            // 3. OVERLAY
            PermCard(
                    icon = Icons.Filled.Visibility,
                    title = "Muncul di Atas Aplikasi Lain (Overlay)",
                    desc =
                            "Izin utama agar karakter Waifu Anda bisa tampil dan bergerak di layar HP.",
                    isGranted = overlayGranted,
                    onRequest = {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
            )

            // 4. WRITE SETTINGS
            PermCard(
                    icon = Icons.Filled.Settings,
                    title = "Ubah Pengaturan Sistem",
                    desc =
                            "Dibutuhkan untuk otomatisasi kontrol media audio dan koneksi remote SSH.",
                    isGranted = settingsGranted,
                    onRequest = {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tombol Konfirmasi Selesai di bagian paling bawah
            Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (allPermissionsGranted) DeepRose
                                            else MaterialTheme.colorScheme.outline
                            )
            ) {
                Text(
                        text =
                                if (allPermissionsGranted) "SELESAI & LANJUTKAN"
                                else "LEWATI SEMENTARA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PermCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        desc: String,
        isGranted: Boolean,
        onRequest: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isGranted) DeepRose.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) DeepRose else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isGranted) DeepRose else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = DeepRose,
                        modifier = Modifier.size(24.dp)
                )
            } else {
                Button(
                        onClick = onRequest,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) { Text("IZINKAN", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
