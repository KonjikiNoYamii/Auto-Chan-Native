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
            Toast.makeText(context, if (ok) "Gambar berhasil diganti" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
        }
        refreshKey++
    }

    fun saveDirect(type: CustomAssetManager.AssetType, uri: Uri) {
        val ok = CustomAssetManager.saveCustom(context, type, uri)
        Toast.makeText(context, if (ok) "Audio berhasil diganti" else "Gagal", Toast.LENGTH_SHORT).show()
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
                val destUri = Uri.fromFile(File(context.cacheDir, "ucrop_${type.key}_${System.currentTimeMillis()}.jpg"))
                val uCropIntent = UCrop.of(uri, destUri).withAspectRatio(1f, 1f).withMaxResultSize(1024, 1024).getIntent(context) ?: return@let
                uCropLauncher.launch(uCropIntent)
            } catch (e: Exception) { saveDirect(type, uri) }
        }
    }

    fun pickImage(type: CustomAssetManager.AssetType) { pendingType = type; pickLauncher.launch("image/*") }
    fun pickAudio(type: CustomAssetManager.AssetType) { pendingType = type; pickLauncher.launch("audio/*") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Assistant", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
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
            // 👤 PROFIL & IDENTITAS
            SettingsGroup("Profil Assistant", Icons.Default.Face) {
                IdentitasFields()
            }

            // 📐 UKURAN OVERLAY
            SettingsGroup("Ukuran Tampilan", Icons.Default.AspectRatio) {
                SizeSliders()
            }

            // 🎨 VISUAL ASSETS
            SettingsGroup("Tampilan Visual", Icons.Default.Palette) {
                AssetRow("Header Menu", CustomAssetManager.AssetType.HEADER, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
                AssetRow("Icon Aplikasi", CustomAssetManager.AssetType.ICON, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
                AssetRow("Chat Icon (AI)", CustomAssetManager.AssetType.CHAT_ICON, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ekspresi Karakter", style = MaterialTheme.typography.labelMedium, color = DeepRose)
                AssetRow("IDLE (Diam)", CustomAssetManager.AssetType.WAIFU_IDLE, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
                AssetRow("HAPPY (Senang)", CustomAssetManager.AssetType.WAIFU_HAPPY, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
                AssetRow("LISTENING (Dengar)", CustomAssetManager.AssetType.WAIFU_LISTENING, refreshKey, onPick = { pickImage(it) }, onReset = { refreshKey++ })
            }

            // 🔊 SUARA & AUDIO
            SettingsGroup("Suara & Audio", Icons.Default.VolumeUp) {
                AssetRow("Sound Pop (Bubble)", CustomAssetManager.AssetType.POP_SOUND, refreshKey, onPick = { pickAudio(it) }, onReset = { refreshKey++ }, isAudio = true)
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(alpha = 0.2f)
                
                // Voice Lines dicitai berdasarkan kategori
                VoiceCategory("Sapaan & Waktu", listOf(
                    "Pagi (Ohayou)" to CustomAssetManager.AssetType.VOICE_MORNING,
                    "Siang (Konnichiwa)" to CustomAssetManager.AssetType.VOICE_AFTERNOON,
                    "Malam (Konbanwa)" to CustomAssetManager.AssetType.VOICE_NIGHT,
                    "Selamat Datang" to CustomAssetManager.AssetType.VOICE_WELCOME_BACK
                ), refreshKey, onPick = { pickAudio(it) }, onReset = { _ -> refreshKey++ }, onLabelChange = { _, _ -> refreshKey++ })

                VoiceCategory("Respon & Jawaban", listOf(
                    "Ya/Baik (Haik)" to CustomAssetManager.AssetType.VOICE_YES,
                    "Ya! (Ceria)" to CustomAssetManager.AssetType.VOICE_YES_HAPPY,
                    "Mengerti (Kyoka)" to CustomAssetManager.AssetType.VOICE_UNDERSTOOD,
                    "Paham (Wakarimashita)" to CustomAssetManager.AssetType.VOICE_UNDERSTOOD_COLD,
                    "Terima Kasih" to CustomAssetManager.AssetType.VOICE_THANKS
                ), refreshKey, onPick = { pickAudio(it) }, onReset = { _ -> refreshKey++ }, onLabelChange = { _, _ -> refreshKey++ })

                VoiceCategory("Reaksi & Kepribadian", listOf(
                    "Harenchi (Ecchi!)" to CustomAssetManager.AssetType.VOICE_ECCHI,
                    "Yamete!" to CustomAssetManager.AssetType.VOICE_YAMETE,
                    "Tertawa (Fufu)" to CustomAssetManager.AssetType.VOICE_LAUGH,
                    "Pujian (Subarashii)" to CustomAssetManager.AssetType.VOICE_GREAT
                ), refreshKey, onPick = { pickAudio(it) }, onReset = { _ -> refreshKey++ }, onLabelChange = { _, _ -> refreshKey++ })

                VoiceCategory("Sistem & Progres", listOf(
                    "Rank Up!" to CustomAssetManager.AssetType.VOICE_RANKUP,
                    "Misi Selesai" to CustomAssetManager.AssetType.VOICE_MISSION_DONE
                ), refreshKey, onPick = { pickAudio(it) }, onReset = { _ -> refreshKey++ }, onLabelChange = { _, _ -> refreshKey++ })
            }

            // ⏰ SAPAAN TEKS
            SettingsGroup("Sapaan Otomatis (Teks)", Icons.Default.AccessTime) {
                GreetingsSection(refreshKey, onUpdate = { refreshKey++ })
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = { CustomAssetManager.resetAll(context); refreshKey++ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepRose),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Semua ke Default")
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = DeepRose, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun IdentitasFields() {
    var name by remember { mutableStateOf(com.silica.assistant.core.config.AssistantConfig.assistantName) }
    var prompt by remember { mutableStateOf(com.silica.assistant.core.llm.LlmConfig.personalityPrompt) }
    var greeting by remember { mutableStateOf(com.silica.assistant.core.config.AssistantConfig.customGreeting) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InputField("Nama Panggilan", name) { name = it; com.silica.assistant.core.config.AssistantConfig.assistantName = it; com.silica.assistant.core.config.AssistantConfig.save() }
        InputField("Sapaan Balik", greeting, "Otomatis...") { greeting = it; com.silica.assistant.core.config.AssistantConfig.customGreeting = it; com.silica.assistant.core.config.AssistantConfig.save() }
        InputField("Kepribadian (Prompt)", prompt, "Sifat AI...") { prompt = it; com.silica.assistant.core.llm.LlmConfig.personalityPrompt = it; com.silica.assistant.core.llm.LlmConfig.save() }
    }
}

@Composable
private fun InputField(label: String, value: String, placeholder: String = "", onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepRose, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DeepRose)
        )
    }
}

@Composable
private fun SizeSliders() {
    var sizeNormal by remember { mutableFloatStateOf(com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault.toFloat()) }
    var sizeGame by remember { mutableFloatStateOf(com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode.toFloat()) }

    Column {
        SliderRow("Ukuran Normal", sizeNormal, 60f..200f) { sizeNormal = it; com.silica.assistant.core.config.AssistantConfig.overlaySizeDefault = it.toInt(); com.silica.assistant.core.config.AssistantConfig.save() }
        Spacer(modifier = Modifier.height(8.dp))
        SliderRow("Ukuran Game", sizeGame, 40f..150f) { sizeGame = it; com.silica.assistant.core.config.AssistantConfig.overlaySizeGameMode = it.toInt(); com.silica.assistant.core.config.AssistantConfig.save() }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp)
        Text("${value.toInt()}dp", fontWeight = FontWeight.Bold, color = DeepRose)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = DeepRose, activeTrackColor = DeepRose))
}

@Composable
private fun VoiceCategory(title: String, items: List<Pair<String, CustomAssetManager.AssetType>>, refreshKey: Int, onPick: (CustomAssetManager.AssetType) -> Unit, onReset: (CustomAssetManager.AssetType) -> Unit, onLabelChange: (CustomAssetManager.AssetType, String) -> Unit) {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.labelMedium, color = DeepRose.copy(alpha = 0.7f))
    items.forEach { (defaultLabel, type) ->
        val customLabel = remember(refreshKey) { CustomAssetManager.getAssetLabel(context, type) }
        AssetRow(if (customLabel.isNotEmpty()) customLabel else defaultLabel, type, refreshKey, onPick = { onPick(it) }, onReset = { onReset(it) }, isAudio = true, onLabelEdit = { onLabelChange(type, it) })
    }
}

@Composable
private fun AssetRow(label: String, type: CustomAssetManager.AssetType, refreshKey: Int, onPick: (CustomAssetManager.AssetType) -> Unit, onReset: (CustomAssetManager.AssetType) -> Unit, isAudio: Boolean = false, onLabelEdit: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val isCustom = remember(refreshKey) { CustomAssetManager.hasCustom(context, type) }
    var isEditing by remember { mutableStateOf(false) }
    var editedLabel by remember { mutableStateOf(label) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(if (isCustom) DeepRose.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(if (isAudio) Icons.Default.Audiotrack else Icons.Default.Image, null, tint = if (isCustom) DeepRose else Color.Gray, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (isEditing && onLabelEdit != null) {
                OutlinedTextField(
                    value = editedLabel, onValueChange = { editedLabel = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall, shape = RoundedCornerShape(8.dp),
                    trailingIcon = { IconButton(onClick = { CustomAssetManager.saveAssetLabel(context, type, editedLabel); onLabelEdit(editedLabel); isEditing = false }) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp)) } }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (onLabelEdit != null) { IconButton(onClick = { isEditing = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(10.dp)) } }
                }
                Text(if (isCustom) "Kustom" else "Default", fontSize = 10.sp, color = if (isCustom) DeepRose else Color.Gray)
            }
        }
        if (isAudio) {
            IconButton(onClick = { SoundManager.playVoice(context, type) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, null, tint = DeepRose) }
        }
        IconButton(onClick = { onPick(type) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (isCustom) {
            IconButton(onClick = { CustomAssetManager.resetCustom(context, type); onReset(type) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, null, tint = DeepRose) }
        }
    }
}

@Composable
private fun GreetingsSection(refreshKey: Int, onUpdate: () -> Unit) {
    val context = LocalContext.current
    val greetings = remember(refreshKey) { CustomAssetManager.getAllCustomGreetings(context) }
    val fields = listOf(Triple("Pagi", "greeting_morning", "Selamat pagi"), Triple("Siang", "greeting_afternoon", "Selamat siang"), Triple("Sore", "greeting_evening", "Selamat sore"), Triple("Malam", "greeting_night", "Selamat malam"))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { (label, key, default) ->
            var text by remember(key, refreshKey) { mutableStateOf(greetings[key] ?: default) }
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                label = { Text(label, fontSize = 12.sp) },
                trailingIcon = { IconButton(onClick = { CustomAssetManager.saveGreeting(context, key, text); onUpdate() }) { Icon(Icons.Default.Save, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) } }
            )
        }
    }
}

@Composable
private fun HorizontalDivider(alpha: Float = 0.1f) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
}
