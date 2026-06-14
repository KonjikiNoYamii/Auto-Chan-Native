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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.CustomAssetManager
import com.silica.assistant.core.system.SoundManager
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
                if (ok) "Gambar berhasil diganti" else "Gagal menyimpan",
                Toast.LENGTH_SHORT
            ).show()
        }
        refreshKey++
    }

    fun saveDirect(type: CustomAssetManager.AssetType, uri: Uri) {
        val ok = CustomAssetManager.saveCustom(context, type, uri)
        Toast.makeText(
            context,
            if (ok) "Audio berhasil diganti" else "Gagal menyimpan audio",
            Toast.LENGTH_SHORT
        ).show()
        refreshKey++
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uri ->
            val type = pendingType ?: return@let
            
            if (type.key.startsWith("voice_") || type == CustomAssetManager.AssetType.POP_SOUND) {
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
                title = { Text("Customize Assistant", fontWeight = FontWeight.Bold) },
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
            SectionHeader("Kepribadian & Identitas", Icons.Default.Face, "Sesuaikan nama dan sifat waifu Anda.")
            IdentitasCard()

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Ukuran Overlay", Icons.Default.AspectRatio, "Sesuaikan ukuran tampilan waifu di layar.")
            SizeControlCard()

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Tampilan Visual", Icons.Default.Palette, "Ganti gambar header, icon, dan ekspresi.")
            
            AssetItem(
                label = "Header Menu",
                type = CustomAssetManager.AssetType.HEADER,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.HEADER),
                onPick = { pickImage(CustomAssetManager.AssetType.HEADER) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.HEADER)
                    refreshKey++
                },
                refreshKey = refreshKey
            )

            AssetItem(
                label = "Icon Aplikasi",
                type = CustomAssetManager.AssetType.ICON,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.ICON),
                onPick = { pickImage(CustomAssetManager.AssetType.ICON) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.ICON)
                    refreshKey++
                },
                refreshKey = refreshKey
            )

            AssetItem(
                label = "Chat Icon (AI)",
                type = CustomAssetManager.AssetType.CHAT_ICON,
                isCustom = CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.CHAT_ICON),
                onPick = { pickImage(CustomAssetManager.AssetType.CHAT_ICON) },
                onReset = {
                    CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.CHAT_ICON)
                    refreshKey++
                },
                refreshKey = refreshKey
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Ekspresi Karakter", style = MaterialTheme.typography.labelLarge, color = DeepRose, modifier = Modifier.padding(start = 4.dp))
            
            AssetItem("IDLE (Diam)", CustomAssetManager.AssetType.WAIFU_IDLE, 
                CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_IDLE), 
                { pickImage(CustomAssetManager.AssetType.WAIFU_IDLE) }, 
                { CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_IDLE); refreshKey++ }, refreshKey)
            
            AssetItem("HAPPY (Senang)", CustomAssetManager.AssetType.WAIFU_HAPPY, 
                CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_HAPPY), 
                { pickImage(CustomAssetManager.AssetType.WAIFU_HAPPY) }, 
                { CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_HAPPY); refreshKey++ }, refreshKey)
            
            AssetItem("LISTENING (Mendengar)", CustomAssetManager.AssetType.WAIFU_LISTENING, 
                CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.WAIFU_LISTENING), 
                { pickImage(CustomAssetManager.AssetType.WAIFU_LISTENING) }, 
                { CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.WAIFU_LISTENING); refreshKey++ }, refreshKey)

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Suara & Audio", Icons.Default.VolumeUp, "Atur suara bubble dan voice lines waifu.")
            
            AssetItem("Sound Pop (Bubble)", CustomAssetManager.AssetType.POP_SOUND, 
                CustomAssetManager.hasCustom(context, CustomAssetManager.AssetType.POP_SOUND), 
                { pickAudio(CustomAssetManager.AssetType.POP_SOUND) }, 
                { CustomAssetManager.resetCustom(context, CustomAssetManager.AssetType.POP_SOUND); refreshKey++ }, refreshKey, isAudio = true)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice Lines (Otomatis)", style = MaterialTheme.typography.labelLarge, color = DeepRose, modifier = Modifier.padding(start = 4.dp))
            
            VoiceList(
                refreshKey = refreshKey,
                onPick = { pickAudio(it) },
                onReset = { CustomAssetManager.resetCustom(context, it); refreshKey++ },
                onLabelChange = { type, label -> CustomAssetManager.saveAssetLabel(context, type, label); refreshKey++ }
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Ucapan Selamat", Icons.Default.AccessTime, "Teks sapaan di menu utama sesuai waktu.")
            GreetingsSection(refreshKey, onUpdate = { refreshKey++ })

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
private fun SectionHeader(title: String, icon: ImageVector, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = DeepRose, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun IdentitasCard() {
    var assistantName by remember { mutableStateOf(com.silica.assistant.core.config.AssistantConfig.assistantName) }
    var personalityPrompt by remember { mutableStateOf(com.silica.assistant.core.llm.LlmConfig.personalityPrompt) }
    var customGreeting by remember { mutableStateOf(com.silica.assistant.core.config.AssistantConfig.customGreeting) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Nama Panggilan", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepRose)
            OutlinedTextField(
                value = assistantName,
                onValueChange = { assistantName = it; com.silica.assistant.core.config.AssistantConfig.assistantName = it; com.silica.assistant.core.config.AssistantConfig.save() },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Sapaan Balik", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepRose)
            OutlinedTextField(
                value = customGreeting,
                onValueChange = { customGreeting = it; com.silica.assistant.core.config.AssistantConfig.customGreeting = it; com.silica.assistant.core.config.AssistantConfig.save() },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Otomatis...", fontSize = 14.sp) },
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("System Prompt (Sifat)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepRose)
            OutlinedTextField(
                value = personalityPrompt,
                onValueChange = { personalityPrompt = it; com.silica.assistant.core.llm.LlmConfig.personalityPrompt = it; com.silica.assistant.core.llm.LlmConfig.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Contoh: Tsundere, galak tapi penyayang...", fontSize = 14.sp) },
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SizeControlCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            var defaultSize by remember { mutableFloatStateOf(com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault.toFloat()) }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Ukuran Normal", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("${defaultSize.toInt()}dp", color = DeepRose, fontWeight = FontWeight.Bold)
            }
            Slider(value = defaultSize, onValueChange = { defaultSize = it; com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault = it.toInt(); com.silica.assistant.core.config.AssistantConfig.save() }, valueRange = 60f..200f)

            Spacer(modifier = Modifier.height(8.dp))
            var gameSize by remember { mutableFloatStateOf(com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode.toFloat()) }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Ukuran Game Mode", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("${gameSize.toInt()}dp", color = DeepRose, fontWeight = FontWeight.Bold)
            }
            Slider(value = gameSize, onValueChange = { gameSize = it; com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode = it.toInt(); com.silica.assistant.core.config.AssistantConfig.save() }, valueRange = 40f..150f)
        }
    }
}

@Composable
private fun VoiceList(
    refreshKey: Int, 
    onPick: (CustomAssetManager.AssetType) -> Unit, 
    onReset: (CustomAssetManager.AssetType) -> Unit,
    onLabelChange: (CustomAssetManager.AssetType, String) -> Unit
) {
    val context = LocalContext.current
    val voices = listOf(
        "Pagi (Ohayou)" to CustomAssetManager.AssetType.VOICE_MORNING,
        "Siang (Konnichiwa)" to CustomAssetManager.AssetType.VOICE_AFTERNOON,
        "Malam (Konbanwa)" to CustomAssetManager.AssetType.VOICE_NIGHT,
        "Terima Kasih (Arigato)" to CustomAssetManager.AssetType.VOICE_THANKS,
        "Sapa Balik (Okairi)" to CustomAssetManager.AssetType.VOICE_WELCOME_BACK,
        "Ya/Baik (Haik)" to CustomAssetManager.AssetType.VOICE_YES,
        "Ya (Haik Happy)" to CustomAssetManager.AssetType.VOICE_YES_HAPPY,
        "Mengerti (Kyoka)" to CustomAssetManager.AssetType.VOICE_UNDERSTOOD,
        "Mengerti (Wakarimashita)" to CustomAssetManager.AssetType.VOICE_UNDERSTOOD_COLD,
        "Yamete!" to CustomAssetManager.AssetType.VOICE_YAMETE,
        "Mesum (Ecchi!)" to CustomAssetManager.AssetType.VOICE_ECCHI,
        "Ketawa (Fufu)" to CustomAssetManager.AssetType.VOICE_LAUGH,
        "Hebat (Subarashii)" to CustomAssetManager.AssetType.VOICE_GREAT,
        "Rank Up!" to CustomAssetManager.AssetType.VOICE_RANKUP,
        "Misi Selesai (Gokurousama)" to CustomAssetManager.AssetType.VOICE_MISSION_DONE,
    )

    voices.forEach { (defaultLabel, type) ->
        val customLabel = remember(refreshKey) { CustomAssetManager.getAssetLabel(context, type) }
        val displayLabel = if (customLabel.isNotEmpty()) customLabel else defaultLabel
        
        AssetItem(
            label = displayLabel,
            type = type,
            isCustom = CustomAssetManager.hasCustom(context, type),
            onPick = { onPick(type) },
            onReset = { onReset(type) },
            refreshKey = refreshKey,
            isAudio = true,
            onLabelEdit = { newLabel -> onLabelChange(type, newLabel) }
        )
    }
}

@Composable
private fun GreetingsSection(refreshKey: Int, onUpdate: () -> Unit) {
    val context = LocalContext.current
    val greetings = remember(refreshKey) { CustomAssetManager.getAllCustomGreetings(context) }
    
    val fields = listOf(
        Triple("Pagi (00:00 - 11:59)", "greeting_morning", "Selamat pagi"),
        Triple("Siang (12:00 - 14:59)", "greeting_afternoon", "Selamat siang"),
        Triple("Sore (15:00 - 17:59)", "greeting_evening", "Selamat sore"),
        Triple("Malam (18:00 - 23:59)", "greeting_night", "Selamat malam")
    )

    fields.forEach { (label, key, default) ->
        GreetingField(label, key, greetings[key] ?: default, { text ->
            CustomAssetManager.saveGreeting(context, key, text)
            onUpdate()
            Toast.makeText(context, "Disimpan", Toast.LENGTH_SHORT).show()
        }, {
            CustomAssetManager.resetGreeting(context, key)
            onUpdate()
        })
    }
}

@Composable
private fun AssetItem(
    label: String,
    type: CustomAssetManager.AssetType,
    isCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
    refreshKey: Int,
    isAudio: Boolean = false,
    onLabelEdit: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var isEditingLabel by remember { mutableStateOf(false) }
    var editedLabel by remember { mutableStateOf(label) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(if (isCustom) Color(0xFF00FF88).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAudio) Icons.Default.Audiotrack else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (isCustom) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingLabel && onLabelEdit != null) {
                        OutlinedTextField(
                            value = editedLabel,
                            onValueChange = { editedLabel = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onLabelEdit(editedLabel)
                                    isEditingLabel = false 
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (onLabelEdit != null) {
                                IconButton(onClick = { isEditingLabel = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Label", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Text(if (isCustom) "Custom Asset" else "Default Asset", fontSize = 11.sp, color = if (isCustom) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                if (isAudio) {
                    IconButton(onClick = { SoundManager.playVoice(context, type) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = DeepRose, modifier = Modifier.size(20.dp))
                    }
                }
                
                TextButton(onClick = onPick) { Text("Ganti", fontSize = 13.sp) }
                if (isCustom) {
                    IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = DeepRose, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingField(label: String, key: String, currentText: String, onSave: (String) -> Unit, onReset: () -> Unit) {
    var text by remember(key) { mutableStateOf(currentText) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DeepRose)
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
                FilledIconButton(onClick = { onSave(text) }, modifier = Modifier.size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF4CAF50))) {
                    Icon(Icons.Filled.Check, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                }
            }
            if (text != currentText) {
                TextButton(onClick = onReset, modifier = Modifier.height(30.dp)) {
                    Text("Reset ke default", fontSize = 11.sp, color = DeepRose)
                }
            }
        }
    }
}
