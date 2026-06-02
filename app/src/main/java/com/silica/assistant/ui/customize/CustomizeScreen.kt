package com.silica.assistant.ui.customize

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.ui.theme.DeepRose
import com.yalantis.ucrop.UCrop
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var refreshKey by remember { mutableIntStateOf(0) }
    var pendingType by remember { mutableStateOf<CustomAssetManager.AssetType?>(null) }

    val uCropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val type = pendingType ?: return@rememberLauncherForActivityResult
        val croppedUri = result.data?.let { UCrop.getOutput(it) }
        if (croppedUri != null) {
            val ok = CustomAssetManager.saveCustom(context, type, croppedUri)
            Toast.makeText(
                context,
                if (ok) "${type.key} diganti" else "Gagal menyimpan",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, "Crop dibatalkan", Toast.LENGTH_SHORT).show()
        }
        refreshKey++
    }

    fun saveDirect(type: CustomAssetManager.AssetType, uri: Uri) {
        val ok = CustomAssetManager.saveCustom(context, type, uri)
        Toast.makeText(
            context,
            if (ok) "${type.key} diganti" else "Gagal",
            Toast.LENGTH_SHORT
        ).show()
        refreshKey++
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uri ->
            val type = pendingType ?: return@let
            pendingType = type

            if (type == CustomAssetManager.AssetType.POP_SOUND) {
                saveDirect(type, uri)
                return@let
            }

            try {
                val destFile = File(context.cacheDir, "ucrop_${type.key}_${System.currentTimeMillis()}.jpg")
                val destUri = Uri.fromFile(destFile)

                val uCropIntent = UCrop.of(uri, destUri)
                    .withAspectRatio(1f, 1f)
                    .withMaxResultSize(1024, 1024)
                    .getIntent(context) ?: return@let

                uCropLauncher.launch(uCropIntent)
            } catch (e: Exception) {
                saveDirect(type, uri)
            }
        }
    }

    fun pickImage(type: CustomAssetManager.AssetType) {
        pendingType = type
        pickLauncher.launch("image/*")
    }

    fun pickAudio(type: CustomAssetManager.AssetType) {
        pendingType = type
        pickLauncher.launch("audio/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize") },
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
                .verticalScroll(scroll)
                .padding(16.dp)
        ) {
            Text(
                "Ganti Tampilan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Pilih gambar atau suara dari galeri/storage Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            AssetItem(
                label = "Header",
                type = CustomAssetManager.AssetType.HEADER,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.HEADER),
                onPick = { pickImage(CustomAssetManager.AssetType.HEADER) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.HEADER)
                    refreshKey++
                    Toast.makeText(context, "Header reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            AssetItem(
                label = "Icon",
                type = CustomAssetManager.AssetType.ICON,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.ICON),
                onPick = { pickImage(CustomAssetManager.AssetType.ICON) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.ICON)
                    refreshKey++
                    Toast.makeText(context, "Icon reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Ganti Karakter Overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "3 ekspresi waifu (IDLE, HAPPY, LISTENING).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            AssetItem(
                label = "IDLE",
                type = CustomAssetManager.AssetType.WAIFU_IDLE,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_IDLE),
                onPick = { pickImage(CustomAssetManager.AssetType.WAIFU_IDLE) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_IDLE)
                    refreshKey++
                    Toast.makeText(context, "IDLE reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            AssetItem(
                label = "HAPPY",
                type = CustomAssetManager.AssetType.WAIFU_HAPPY,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_HAPPY),
                onPick = { pickImage(CustomAssetManager.AssetType.WAIFU_HAPPY) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_HAPPY)
                    refreshKey++
                    Toast.makeText(context, "HAPPY reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            AssetItem(
                label = "LISTENING",
                type = CustomAssetManager.AssetType.WAIFU_LISTENING,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_LISTENING),
                onPick = { pickImage(CustomAssetManager.AssetType.WAIFU_LISTENING) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_LISTENING)
                    refreshKey++
                    Toast.makeText(context, "LISTENING reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Ganti Suara Bubble",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Suara \"pop\" saat bubble muncul.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            AssetItem(
                label = "Pop Sound",
                type = CustomAssetManager.AssetType.POP_SOUND,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.POP_SOUND),
                onPick = { pickAudio(CustomAssetManager.AssetType.POP_SOUND) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.POP_SOUND)
                    refreshKey++
                    Toast.makeText(context, "Pop sound reset ke default", Toast.LENGTH_SHORT).show()
                },
                refreshKey = refreshKey
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Ubah Ucapan Selamat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Custom teks yang muncul di halaman utama sesuai waktu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            val greetings = remember(refreshKey) {
                CustomAssetManager.getAllCustomGreetings(context)
            }

            GreetingField(
                label = "Pagi (00:00 - 11:59)",
                key = "greeting_morning",
                currentText = greetings["greeting_morning"] ?: "Selamat pagi",
                onSave = { text ->
                    CustomAssetManager.saveGreeting(context, "greeting_morning", text)
                    refreshKey++
                    Toast.makeText(context, "Ucapan pagi disimpan", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    CustomAssetManager.resetGreeting(context, "greeting_morning")
                    refreshKey++
                }
            )

            GreetingField(
                label = "Siang (12:00 - 14:59)",
                key = "greeting_afternoon",
                currentText = greetings["greeting_afternoon"] ?: "Selamat siang",
                onSave = { text ->
                    CustomAssetManager.saveGreeting(context, "greeting_afternoon", text)
                    refreshKey++
                    Toast.makeText(context, "Ucapan siang disimpan", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    CustomAssetManager.resetGreeting(context, "greeting_afternoon")
                    refreshKey++
                }
            )

            GreetingField(
                label = "Sore (15:00 - 17:59)",
                key = "greeting_evening",
                currentText = greetings["greeting_evening"] ?: "Selamat sore",
                onSave = { text ->
                    CustomAssetManager.saveGreeting(context, "greeting_evening", text)
                    refreshKey++
                    Toast.makeText(context, "Ucapan sore disimpan", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    CustomAssetManager.resetGreeting(context, "greeting_evening")
                    refreshKey++
                }
            )

            GreetingField(
                label = "Malam (18:00 - 23:59)",
                key = "greeting_night",
                currentText = greetings["greeting_night"] ?: "Selamat malam",
                onSave = { text ->
                    CustomAssetManager.saveGreeting(context, "greeting_night", text)
                    refreshKey++
                    Toast.makeText(context, "Ucapan malam disimpan", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    CustomAssetManager.resetGreeting(context, "greeting_night")
                    refreshKey++
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    CustomAssetManager.resetAll(context)
                    refreshKey++
                    Toast.makeText(context, "Semua asset direset ke default", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose)
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Semua ke Default")
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun AssetItem(
    label: String,
    type: CustomAssetManager.AssetType,
    isCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    refreshKey: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isCustom) Icons.Filled.CheckCircle else Icons.Filled.Image,
                contentDescription = null,
                tint = if (isCustom) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    if (isCustom) "Custom" else "Default",
                    fontSize = 11.sp,
                    color = if (isCustom) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onPick) { Text("Ganti") }
            if (isCustom) {
                TextButton(onClick = onReset) { Text("Reset", color = DeepRose) }
            }
        }
    }
}

@Composable
private fun GreetingField(
    label: String,
    key: String,
    currentText: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var text by remember(key) { mutableStateOf(currentText) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { onSave(text) },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DeepRose)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                }
            }
            if (text != currentText) {
                TextButton(onClick = onReset) {
                    Text("Reset ke default", fontSize = 11.sp, color = DeepRose)
                }
            }
        }
    }
}
