package com.silica.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CommandInputSection(
    commandText: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Command",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commandText,
                onValueChange = onCommandChange,
                label = { Text("Enter command...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth(),
                enabled = commandText.isNotBlank(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Execute")
            }
        }
    }
}
