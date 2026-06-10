package com.silica.assistant.ui.ssh

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.silica.assistant.ui.theme.DeepRose

@Composable
fun ActionConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DeepRose)
            ) {
                Text("Setujui & Jalankan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tolak")
            }
        }
    )
}
