package com.silica.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.silica.assistant.core.CommandManager

@Composable
fun MainScreen() {

    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text("AI Assistant Online")

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            CommandManager.execute(context, "open_spotify")
        }) {
            Text("Open Spotify")
        }
        Button(onClick = {
    CommandManager.execute(context, "start_overlay")
    }) {
    Text("Start Waifu Overlay")
}
    }
}