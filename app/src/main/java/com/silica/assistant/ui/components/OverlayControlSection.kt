package com.silica.assistant.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silica.assistant.core.CommandManager
import com.silica.assistant.ui.theme.DeepRose

@Composable
fun OverlayControlSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showOverlayDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Overlay tetap berjalan tanpa notifikasi", Toast.LENGTH_SHORT).show()
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Izin Overlay", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Aplikasi ini membutuhkan izin Muncul di Atas Aplikasi Lain " +
                    "untuk menampilkan karakter waifu di layar HP Anda. " +
                    "Aktifkan izin tersebut di pengaturan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
                ) {
                    Text("BUKA PENGATURAN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) {
                    Text("BATAL")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (Settings.canDrawOverlays(context)) {
                            CommandManager.execute(context, "start_overlay")
                        } else {
                            showOverlayDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start")
                }

                OutlinedButton(
                    onClick = { CommandManager.execute(context, "stop_overlay") },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Close")
                }
            }
        }
    }
}
