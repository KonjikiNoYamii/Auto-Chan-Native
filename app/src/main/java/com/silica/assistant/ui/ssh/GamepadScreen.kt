package com.silica.assistant.ui.ssh

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.silica.assistant.core.ssh.SshManager
import com.yalantis.ucrop.UCrop
import java.io.File
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class GamepadLayout(
    val type: String,
    val label: String,
    val value: String,
    val x: Float,
    val y: Float
)

private fun hapticClick(context: Context) {
    try {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vib?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib?.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib?.vibrate(15)
        }
    } catch (_: Exception) {}
}

private var activeKeys = mutableSetOf<String>()

private fun handleJoystickMove(x: Float, y: Float, scope: CoroutineScope) {
    val threshold = 0.3f
    val newKeys = mutableSetOf<String>()
    if (y < -threshold) newKeys.add("w")
    if (y > threshold) newKeys.add("s")
    if (x < -threshold) newKeys.add("a")
    if (x > threshold) newKeys.add("d")
    val toRelease = activeKeys - newKeys
    val toPress = newKeys - activeKeys
    if (toRelease.isNotEmpty() || toPress.isNotEmpty()) {
        scope.launch(Dispatchers.IO) {
            toRelease.forEach { SshManager.executeCommand("export DISPLAY=:0 && xdotool keyup $it") }
            toPress.forEach { SshManager.executeCommand("export DISPLAY=:0 && xdotool keydown $it") }
        }
    }
    activeKeys = newKeys
}

private fun handleJoystickRight(x: Float, y: Float, sensitivity: Float, scope: CoroutineScope) {
    val deadZone = 0.15f
    val fx = if (abs(x) > deadZone) x else 0f
    val fy = if (abs(y) > deadZone) y else 0f
    val dx = (fx * sensitivity).toInt()
    val dy = (fy * sensitivity).toInt()
    if (abs(dx) > 0 || abs(dy) > 0) {
        scope.launch(Dispatchers.IO) {
            SshManager.executeCommand("export DISPLAY=:0 && xdotool mousemove_relative -- $dx $dy")
        }
    }
}

private fun handleMouseMove(dx: Float, dy: Float, sensitivity: Float, scope: CoroutineScope) {
    val sdx = (dx * sensitivity).toInt()
    val sdy = (dy * sensitivity).toInt()
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool mousemove_relative -- $sdx $sdy")
    }
}

private fun handleMouseClick(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool click 1")
    }
}

private fun handleMouseRightClick(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool click 3")
    }
}

private fun handleScroll(delta: Int, scope: CoroutineScope) {
    val btn = if (delta < 0) "5" else "4"
    repeat(abs(delta).coerceIn(1, 5)) {
        scope.launch(Dispatchers.IO) {
            SshManager.executeCommand("export DISPLAY=:0 && xdotool click $btn")
        }
    }
}

private fun sendKeyDown(key: String, scope: CoroutineScope) {
    activeKeys.add(key)
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool keydown $key")
    }
}

private fun sendKeyUp(key: String, scope: CoroutineScope) {
    activeKeys.remove(key)
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool keyup $key")
    }
}

private fun sendKeyTap(key: String, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        SshManager.executeCommand("export DISPLAY=:0 && xdotool key $key")
    }
}

fun saveLayouts(context: Context, layouts: List<GamepadLayout>) {
    val arr = JSONArray()
    layouts.forEach {
        val obj = JSONObject()
        obj.put("type", it.type)
        obj.put("label", it.label)
        obj.put("value", it.value)
        obj.put("x", it.x)
        obj.put("y", it.y)
        arr.put(obj)
    }
    context.getSharedPreferences("gamepad_prefs", Context.MODE_PRIVATE).edit()
        .putString("layout_json", arr.toString())
        .apply()
}

fun loadLayouts(context: Context, reset: Boolean = false): List<GamepadLayout> {
    val json = if (reset) null else context.getSharedPreferences("gamepad_prefs", Context.MODE_PRIVATE)
        .getString("layout_json", null)
    if (json == null) return generateDefaultLayout(context)
    val list = mutableListOf<GamepadLayout>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(GamepadLayout(
                obj.getString("type"),
                obj.getString("label"),
                obj.getString("value"),
                obj.getDouble("x").toFloat(),
                obj.getDouble("y").toFloat()
            ))
        }
    } catch (_: Exception) {
        return generateDefaultLayout(context)
    }
    return list
}

private fun generateDefaultLayout(context: Context): List<GamepadLayout> {
    val metrics = context.resources.displayMetrics
    val w = metrics.widthPixels / metrics.density
    val h = ((metrics.heightPixels / metrics.density) - 56f).coerceAtMost(360f)

    val margin = 14f
    val marginR = 50f

    val jsSize = min(105f, h * 0.30f)
    val tpH = (h * 0.20f).coerceIn(60f, 75f)
    val dpadSize = min(90f, h * 0.26f)

    val leftJx = margin
    val rightJx = w - jsSize - marginR
    val trigRx = w - 44f - marginR

    val jsY = 34f
    val tpY = 34f
    val dpadY = h - dpadSize - 14f
    val keysY1 = tpY + tpH + 12f
    val keysY2 = keysY1 + 40f
    val keysY3 = keysY2 + 40f
    val centerX = (w / 3f) + 10f

    return listOf(
        GamepadLayout("trigger", "L1", "q", margin, 0f),
        GamepadLayout("trigger", "L2", "e", margin + 48f, 0f),
        GamepadLayout("trigger", "R1", "r", trigRx - 50f, 0f),
        GamepadLayout("trigger", "R2", "f", trigRx, 0f),
        GamepadLayout("joystick", "WASD", "", leftJx, jsY),
        GamepadLayout("dpad", "DPad", "Up,Down,Left,Right", leftJx, dpadY),
        GamepadLayout("joystick_right", "Mouse Look", "", rightJx, jsY),
        GamepadLayout("touchpad", "Mouse", "", centerX, tpY),
        GamepadLayout("key", "SPACE", "space", centerX, keysY1),
        GamepadLayout("key", "SHIFT", "Shift_L", centerX + 52f, keysY1),
        GamepadLayout("key", "CTRL", "Control_L", centerX + 104f, keysY1),
        GamepadLayout("key", "TAB", "Tab", centerX, keysY2),
        GamepadLayout("key", "ENTER", "Return", centerX + 52f, keysY2),
        GamepadLayout("key", "1", "1", centerX + 104f, keysY2),
        GamepadLayout("key", "2", "2", centerX + 150f, keysY2),
        GamepadLayout("key", "3", "3", centerX + 196f, keysY2),
        GamepadLayout("key", "4", "4", centerX + 242f, keysY2),
        GamepadLayout("key", "E", "e", centerX, keysY3),
        GamepadLayout("key", "Q", "q", centerX + 52f, keysY3),
        GamepadLayout("key", "R", "r", centerX + 104f, keysY3),
        GamepadLayout("key", "F", "f", centerX + 150f, keysY3),
        GamepadLayout("key", "ESC", "Escape", centerX + 196f, keysY3),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gamepadPrefs = context.getSharedPreferences("gamepad_prefs", Context.MODE_PRIVATE)

    var backgroundUri by remember { mutableStateOf(gamepadPrefs.getString("bg_uri", null)) }
    var bgScaleIndex by remember { mutableIntStateOf(gamepadPrefs.getInt("bg_scale", 0)) }

    val uCropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val croppedUri = result.data?.let { UCrop.getOutput(it) }
        if (croppedUri != null) {
            try {
                val dest = File(context.filesDir, "gamepad_bg.jpg")
                context.contentResolver.openInputStream(croppedUri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                backgroundUri = Uri.fromFile(dest).toString()
                gamepadPrefs.edit().putString("bg_uri", backgroundUri).apply()
                Toast.makeText(context, "Background saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val destUri = Uri.fromFile(File(context.cacheDir, "ucrop_bg_${System.currentTimeMillis()}.jpg"))
                val options = UCrop.Options()
                options.setFreeStyleCropEnabled(true)
                options.setHideBottomControls(false)
                val intent = UCrop.of(it, destUri)
                    .withAspectRatio(16f, 9f)
                    .withMaxResultSize(2400, 1350)
                    .withOptions(options)
                    .getIntent(context)
                uCropLauncher.launch(intent)
            } catch (e: Exception) {
                backgroundUri = it.toString()
                gamepadPrefs.edit().putString("bg_uri", it.toString()).apply()
            }
        }
    }

    fun pickBackground() { pickLauncher.launch("image/*") }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    var isEditMode by remember { mutableStateOf(false) }
    var layouts by remember { mutableStateOf(loadLayouts(context)) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var mouseSensitivity by remember { mutableFloatStateOf(gamepadPrefs.getFloat("mouse_sensitivity", 8f)) }
    var lookSensitivity by remember { mutableFloatStateOf(gamepadPrefs.getFloat("look_sensitivity", 12f)) }

    var gestureLeft by remember { mutableStateOf(gamepadPrefs.getString("gesture_left", "space") ?: "space") }
    var gestureRight by remember { mutableStateOf(gamepadPrefs.getString("gesture_right", "Escape") ?: "Escape") }
    var gestureUp by remember { mutableStateOf(gamepadPrefs.getString("gesture_up", "f") ?: "f") }
    var gestureDown by remember { mutableStateOf(gamepadPrefs.getString("gesture_down", "Tab") ?: "Tab") }
    var gestureToast by remember { mutableStateOf("") }

    LaunchedEffect(gestureToast) {
        if (gestureToast.isNotEmpty()) {
            delay(1200)
            gestureToast = ""
        }
    }

    var batchDx by remember { mutableFloatStateOf(0f) }
    var batchDy by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30)
            val dx = batchDx.toInt()
            val dy = batchDy.toInt()
            if (dx != 0 || dy != 0) {
                batchDx -= dx
                batchDy -= dy
                scope.launch(Dispatchers.IO) {
                    SshManager.executeCommand("export DISPLAY=:0 && xdotool mousemove_relative -- $dx $dy")
                }
            }
        }
    }

    val componentSizes = remember {
        val hDp = ((context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density) - 56f).coerceAtMost(360f)
        val wDp = context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
        ComponentSizes(
            joystick = min(105f, hDp * 0.30f),
            touchpadW = (wDp * 0.25f).coerceIn(140f, 180f),
            touchpadH = (hDp * 0.20f).coerceIn(60f, 75f),
            dpad = min(90f, hDp * 0.26f),
            triggerW = 44f,
            triggerH = 28f,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Gamepad", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { pickBackground() }) {
                            Icon(Icons.Default.Image, contentDescription = "Background", tint = Color.White)
                        }
                        if (backgroundUri != null) {
                            IconButton(onClick = {
                                backgroundUri = null
                                gamepadPrefs.edit().remove("bg_uri").apply()
                            }) {
                                Icon(Icons.Default.HideImage, contentDescription = "Clear", tint = Color.White)
                            }
                            val scaleIcons = listOf(Icons.Default.Crop, Icons.Default.FitScreen, Icons.Default.Straighten, Icons.Default.CenterFocusStrong)
                            val scaleLabels = listOf("Crop", "Fit", "Fill", "Center")
                            IconButton(onClick = {
                                bgScaleIndex = (bgScaleIndex + 1) % 4
                                gamepadPrefs.edit().putInt("bg_scale", bgScaleIndex).apply()
                            }) {
                                Icon(scaleIcons[bgScaleIndex], contentDescription = scaleLabels[bgScaleIndex], tint = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            layouts = loadLayouts(context, true)
                            saveLayouts(context, layouts)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                        }
                        IconButton(onClick = { showAddKeyDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Key", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        isEditMode = !isEditMode
                        if (!isEditMode) saveLayouts(context, layouts)
                    }) {
                        Icon(if (isEditMode) Icons.Default.Save else Icons.Default.Edit, contentDescription = "Edit Mode", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.7f))
            )
        },
        containerColor = Espresso
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundUri != null) {
                val scales = listOf(ContentScale.Crop, ContentScale.Fit, ContentScale.FillBounds, ContentScale.Inside)
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = scales[bgScaleIndex.coerceIn(0, 3)]
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Espresso, Color.Black))
                ))
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                GestureOverlay(
                    gestureLeft = gestureLeft,
                    gestureRight = gestureRight,
                    gestureUp = gestureUp,
                    gestureDown = gestureDown,
                    onTrigger = { key, dir ->
                        hapticClick(context)
                        sendKeyTap(key, scope)
                        gestureToast = when (dir) {
                            "left" -> "← $key"
                            "right" -> "→ $key"
                            "up" -> "↑ $key"
                            "down" -> "↓ $key"
                            else -> key
                        }
                    }
                )

                layouts.forEachIndexed { index, layout ->
                    DraggableComponent(
                        layout = layout,
                        isEditMode = isEditMode,
                        onPositionChange = { newOffset ->
                            val newList = layouts.toMutableList()
                            newList[index] = layout.copy(x = newOffset.x, y = newOffset.y)
                            layouts = newList
                        },
                        onDelete = {
                            val newList = layouts.toMutableList()
                            newList.removeAt(index)
                            layouts = newList
                        }
                    ) {
                        when (layout.type) {
                            "joystick" -> Joystick(
                                size = componentSizes.joystick,
                                mode = JoystickMode.WASD,
                                onMove = { x, y -> handleJoystickMove(x, y, scope) }
                            )
                            "joystick_right" -> Joystick(
                                size = componentSizes.joystick,
                                mode = JoystickMode.MOUSE,
                                onMove = { x, y ->
                                    val dz = 0.15f
                                    if (abs(x) > dz) batchDx += x * lookSensitivity
                                    if (abs(y) > dz) batchDy += y * lookSensitivity
                                }
                            )
                            "touchpad" -> ModernTouchpad(
                                widthDp = componentSizes.touchpadW,
                                heightDp = componentSizes.touchpadH,
                                onMove = { dx, dy -> batchDx += dx * mouseSensitivity; batchDy += dy * mouseSensitivity },
                                onClick = { handleMouseClick(scope) },
                                onRightClick = { handleMouseRightClick(scope) },
                                onScroll = { delta -> handleScroll(delta, scope) }
                            )
                            "dpad" -> DPad(
                                size = componentSizes.dpad,
                                context = context,
                                scope = scope
                            )
                            "trigger" -> TriggerButton(
                                label = layout.label,
                                key = layout.value,
                                widthDp = componentSizes.triggerW,
                                heightDp = componentSizes.triggerH,
                                context = context,
                                scope = scope
                            )
                            "key" -> GamepadKey(
                                label = layout.label,
                                xKey = layout.value,
                                context = context,
                                scope = scope
                            )
                        }
                    }
                }

                if (isEditMode) {
                    Text(
                        "EDIT MODE: Drag to reposition",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(6.dp),
                        color = Color.Yellow, fontSize = 10.sp
                    )
                }

                if (gestureToast.isNotEmpty()) {
                    Text(
                        gestureToast,
                        modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showAddKeyDialog) {
        AddKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onAdd = { label, key, x, y ->
                layouts = layouts + GamepadLayout("key", label, key, x, y)
                showAddKeyDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        var tempMouse by remember { mutableFloatStateOf(mouseSensitivity) }
        var tempLook by remember { mutableFloatStateOf(lookSensitivity) }
        var tempGL by remember { mutableStateOf(gestureLeft) }
        var tempGR by remember { mutableStateOf(gestureRight) }
        var tempGU by remember { mutableStateOf(gestureUp) }
        var tempGD by remember { mutableStateOf(gestureDown) }
        var showGestureHelp by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Touchpad / Mouse", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Slider(value = tempMouse, onValueChange = { tempMouse = it }, valueRange = 1f..25f, steps = 23)
                    Text("${tempMouse.toInt()}x", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Text("Right Joystick (Look)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Slider(value = tempLook, onValueChange = { tempLook = it }, valueRange = 1f..30f, steps = 28)
                    Text("${tempLook.toInt()}x", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = 0.3f)))
                    Spacer(Modifier.height(12.dp))
                    Text("Quick Gestures", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Gesek cepat (>80px, <500ms) di area kosong untuk trigger SSH key", fontSize = 10.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    GestureField("← Gesek Kiri", tempGL, { tempGL = it })
                    GestureField("→ Gesek Kanan", tempGR, { tempGR = it })
                    GestureField("↑ Gesek Atas", tempGU, { tempGU = it })
                    GestureField("↓ Gesek Bawah", tempGD, { tempGD = it })
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showGestureHelp = true }) { Text("Keyboard key names?", fontSize = 10.sp) }
                    if (showGestureHelp) {
                        Text("Gunakan nama key xdotool: space, Return, Escape, Tab, F1-F12, a-z, 0-9, Shift_L, Control_L, Alt_L, BackSpace, Delete, comma, period, slash, bracketleft, bracketright", fontSize = 9.sp, color = Color.Gray, lineHeight = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mouseSensitivity = tempMouse
                    lookSensitivity = tempLook
                    gestureLeft = tempGL; gestureRight = tempGR; gestureUp = tempGU; gestureDown = tempGD
                    gamepadPrefs.edit().run {
                        putFloat("mouse_sensitivity", tempMouse)
                        putFloat("look_sensitivity", tempLook)
                        putString("gesture_left", tempGL)
                        putString("gesture_right", tempGR)
                        putString("gesture_up", tempGU)
                        putString("gesture_down", tempGD)
                        apply()
                    }
                    showSettingsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private data class ComponentSizes(
    val joystick: Float,
    val touchpadW: Float,
    val touchpadH: Float,
    val dpad: Float,
    val triggerW: Float,
    val triggerH: Float,
)

@Composable
private fun GestureField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(20)) },
            modifier = Modifier.width(120.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun GestureOverlay(
    gestureLeft: String,
    gestureRight: String,
    gestureUp: String,
    gestureDown: String,
    onTrigger: (key: String, dir: String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gestureLeft, gestureRight, gestureUp, gestureDown) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        val startY = down.position.y
                        val startNs = System.nanoTime()
                        var lastX = startX
                        var lastY = startY
                        var wasSwipe = false

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                lastX = change.position.x
                                lastY = change.position.y
                            } else {
                                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                                val dx = lastX - startX
                                val dy = lastY - startY
                                val adx = if (dx > 0) dx else -dx
                                val ady = if (dy > 0) dy else -dy
                                if (elapsedMs < 500 && maxOf(adx, ady) > 80f) {
                                    if (adx > ady) {
                                        onTrigger(if (dx < 0) gestureLeft else gestureRight, if (dx < 0) "left" else "right")
                                    } else {
                                        onTrigger(if (dy < 0) gestureUp else gestureDown, if (dy < 0) "up" else "down")
                                    }
                                    wasSwipe = true
                                }
                                break
                            }
                        } while (true)

                        if (!wasSwipe) {
                            down.consume()
                        }
                    }
                }
            }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DraggableComponent(
    layout: GamepadLayout,
    isEditMode: Boolean,
    onPositionChange: (Offset) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    var lastTouch by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset {
                with(density) {
                    IntOffset(layout.x.dp.roundToPx(), layout.y.dp.roundToPx())
                }
            }
    ) {
        content()
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, Color.Yellow.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .background(Color.Yellow.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                lastTouch = Offset(event.rawX, event.rawY); true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.rawX - lastTouch.x
                                val dy = event.rawY - lastTouch.y
                                with(density) {
                                    val newX = layout.x + dx.toDp().value
                                    val newY = layout.y + dy.toDp().value
                                    currentOnPositionChange(Offset(newX, newY))
                                }
                                lastTouch = Offset(event.rawX, event.rawY); true
                            }
                            else -> true
                        }
                    }
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(24.dp)
                    .background(Color.Red, CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ====================================
// JOYSTICK (Left WASD / Right Mouse)
// ====================================
enum class JoystickMode { WASD, MOUSE }

@Composable
fun Joystick(
    size: Float = 120f,
    mode: JoystickMode = JoystickMode.WASD,
    onMove: (x: Float, y: Float) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    val radius = size / 2
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Color.Gray.copy(alpha = 0.15f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .pointerInput(mode) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offset + dragAmount
                        val distance = sqrt(newOffset.x.pow(2) + newOffset.y.pow(2))
                        val maxDist = radius * density.density
                        if (distance <= maxDist) {
                            offset = newOffset
                        } else {
                            val angle = atan2(newOffset.y, newOffset.x)
                            offset = Offset(cos(angle) * maxDist, sin(angle) * maxDist)
                        }
                        onMove(offset.x / maxDist, offset.y / maxDist)
                    },
                    onDragEnd = {
                        offset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        offset = Offset.Zero
                        onMove(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { with(density) { IntOffset(offset.x.toDp().roundToPx(), offset.y.toDp().roundToPx()) } }
                .size((size / 2).dp)
                .background(
                    if (mode == JoystickMode.MOUSE) Color(0xFF4A90D9) else DeepRose,
                    CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
        if (size >= 100f) {
            Text(
                if (mode == JoystickMode.WASD) "WASD" else "LOOK",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 9.sp
            )
        }
    }
}

// ====================================
// MODERN TOUCHPAD (smaller + scroll)
// ====================================
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ModernTouchpad(
    widthDp: Float = 200f,
    heightDp: Float = 80f,
    onMove: (dx: Float, dy: Float) -> Unit,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (delta: Int) -> Unit
) {
    var lastPos by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var pointerCount by remember { mutableIntStateOf(0) }
    var scrollAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(widthDp.dp, heightDp.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        pointerCount = event.pointerCount
                        if (pointerCount == 1) {
                            lastPos = Offset(event.getX(0), event.getY(0))
                            isDragging = false
                            scrollAccum = 0f
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (pointerCount == 1) {
                            val dx = event.x - lastPos.x
                            val dy = event.y - lastPos.y
                            scrollAccum += dy
                            if (abs(scrollAccum) > 30f) {
                                onScroll(scrollAccum.toInt())
                                scrollAccum = 0f
                            }
                            if (abs(dx) > 1f || abs(dy) > 1f) {
                                onMove(dx, dy)
                                isDragging = true
                            }
                            lastPos = Offset(event.x, event.y)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (pointerCount == 1 && !isDragging) {
                            onClick()
                        }
                        pointerCount = 0
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (pointerCount == 2 && !isDragging) {
                            onRightClick()
                        }
                        pointerCount--
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("Touchpad", color = Color.White.copy(alpha = 0.2f), fontSize = 10.sp)
        Text(
            "← drag · ↑↓ scroll · 2-finger tap →",
            color = Color.White.copy(alpha = 0.12f),
            fontSize = 7.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}

// ====================================
// D-PAD
// ====================================
@Composable
fun DPad(
    size: Float = 90f,
    context: Context,
    scope: CoroutineScope
) {
    val padSize = size.dp
    val btnSize = (size / 3.2f).dp
    val gap = (size / 14f).dp
    val centerSize = (size / 4f).dp

    Box(
        modifier = Modifier.size(padSize),
        contentAlignment = Alignment.Center
    ) {
        // Up
        DPadButton(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = gap / 2),
            size = btnSize,
            label = "▲",
            onDown = { sendKeyDown("Up", scope); hapticClick(context) },
            onUp = { sendKeyUp("Up", scope) }
        )
        // Down
        DPadButton(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-gap / 2)),
            size = btnSize,
            label = "▼",
            onDown = { sendKeyDown("Down", scope); hapticClick(context) },
            onUp = { sendKeyUp("Down", scope) }
        )
        // Left
        DPadButton(
            modifier = Modifier.align(Alignment.CenterStart).offset(x = gap / 2),
            size = btnSize,
            label = "◀",
            onDown = { sendKeyDown("Left", scope); hapticClick(context) },
            onUp = { sendKeyUp("Left", scope) }
        )
        // Right
        DPadButton(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-gap / 2)),
            size = btnSize,
            label = "▶",
            onDown = { sendKeyDown("Right", scope); hapticClick(context) },
            onUp = { sendKeyUp("Right", scope) }
        )
        // Center
        Box(
            modifier = Modifier
                .size(centerSize)
                .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DPadButton(
    modifier: Modifier,
    size: Dp,
    label: String,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isPressed) DeepRose.copy(alpha = 0.7f)
                else Color.Gray.copy(alpha = 0.25f),
                CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { isPressed = true; onDown(); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPressed = false; onUp(); true }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = (size.value / 2.2f).sp)
    }
}

// ====================================
// TRIGGER BUTTON (L1/L2/R1/R2)
// ====================================
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TriggerButton(
    label: String,
    key: String,
    widthDp: Float = 50f,
    heightDp: Float = 34f,
    context: Context,
    scope: CoroutineScope
) {
    var isPressed by remember { mutableStateOf(false) }
    val isLeft = label.startsWith("L")

    Surface(
        modifier = Modifier
            .size(widthDp.dp, heightDp.dp)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        hapticClick(context)
                        sendKeyDown(key, scope)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        sendKeyUp(key, scope)
                        true
                    }
                    else -> false
                }
            },
        color = if (isPressed) DeepRose else Color.Gray.copy(alpha = 0.25f),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (isLeft) Color(0xFF66BBFF) else Color(0xFFFF8866),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ====================================
// GAMEPAD KEY (with haptic)
// ====================================
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadKey(
    label: String,
    xKey: String,
    context: Context,
    scope: CoroutineScope
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .size(
                width = if (label.length > 3) 60.dp else if (label.length > 2) 52.dp else 44.dp,
                height = 38.dp
            )
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        hapticClick(context)
                        sendKeyDown(xKey, scope)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        sendKeyUp(xKey, scope)
                        true
                    }
                    else -> false
                }
            },
        color = if (isPressed) DeepRose else Color.Gray.copy(alpha = 0.25f),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (label.length > 3) 8.sp else 10.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ====================================
// ADD KEY DIALOG
// ====================================
@Composable
private fun AddKeyDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, key: String, x: Float, y: Float) -> Unit
) {
    var keyLabel by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf("Escape") }
    val commonKeys = listOf(
        "Escape", "Return", "space", "BackSpace", "Tab",
        "Up", "Down", "Left", "Right",
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "Shift_L", "Control_L", "Alt_L", "super"
    )
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Key") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyLabel,
                    onValueChange = { keyLabel = it },
                    label = { Text("Label (e.g. ESC, ENTER)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Key: $selectedKey")
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        commonKeys.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key) },
                                onClick = {
                                    selectedKey = key
                                    if (keyLabel.isBlank()) keyLabel = key.uppercase()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (keyLabel.isNotBlank()) {
                    onAdd(keyLabel, selectedKey, 100f, 100f)
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
