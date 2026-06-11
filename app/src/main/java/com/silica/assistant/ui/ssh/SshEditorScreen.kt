package com.silica.assistant.ui.ssh

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.ssh.SshManager
import com.silica.assistant.ui.theme.DeepRose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshEditorScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var terminalOutput by remember { mutableStateOf(SshManager.getShell()?.getFullOutput() ?: "") }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    var commandHistory = remember { mutableListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }
    var proposedAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    val fileName = filePath.substringAfterLast("/")

    DisposableEffect(Unit) {
        val shell = SshManager.getShell()
        val listener: (String) -> Unit = { data ->
            terminalOutput += data
        }
        shell?.addOutputListener(listener)
        onDispose { shell?.removeOutputListener(listener) }
    }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            SshManager.readFileContent(filePath)
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        content = it
                        isLoading = false
                    }
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal membaca file: ${e.message}", Toast.LENGTH_LONG).show()
                        onBack()
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(filePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isLoading) {
                        IconButton(onClick = { showTerminal = !showTerminal }) {
                            Icon(Icons.Filled.Terminal, contentDescription = "Terminal", tint = if (showTerminal) DeepRose else Color.White)
                        }
                        IconButton(
                            onClick = {
                                isSaving = true
                                scope.launch(Dispatchers.IO) {
                                    SshManager.saveFileContent(filePath, content)
                                        .onSuccess {
                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                Toast.makeText(context, "Tersimpan", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .onFailure { e ->
                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Save, contentDescription = "Save", tint = DeepRose)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(if (showTerminal) 0.6f else 1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DeepRose)
                } else {
                    val scrollState = rememberScrollState()
                    
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp),
                        textStyle = TextStyle(
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        visualTransformation = CodeHighlightTransformation(fileName)
                    )
                }
            }
            
            if (showTerminal) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    val terminalColor = Color(0xFFCCCCCC)
                    val errorColor = Color(0xFFFF6B6B)
                    val cmdColor = Color(0xFF00FF88)
                    val promptColor = Color(0xFFFFD700)
                    
                    val annotatedTerminal = remember(terminalOutput) {
                        buildAnnotatedString {
                            var currentPos = 0
                            val ansiRegex = Regex("\\u001B\\[[?;\\d]*[A-Za-z]|\\u001B\\][0-9];.*?(\\u0007|\\u001B\\\\)")
                            val matches = ansiRegex.findAll(terminalOutput).toList()
                            var currentColor = terminalColor
                            
                            for (match in matches) {
                                val textBefore = terminalOutput.substring(currentPos, match.range.first)
                                if (textBefore.isNotEmpty()) {
                                    withStyle(SpanStyle(color = currentColor)) { append(textBefore) }
                                }
                                
                                val ansiCode = match.value
                                if (ansiCode.startsWith("\u001B[") && ansiCode.endsWith("m")) {
                                    val content = ansiCode.substring(2, ansiCode.length - 1)
                                    if (content.isEmpty() || content == "0" || content == "00") {
                                        currentColor = terminalColor
                                    } else {
                                        for (code in content.split(";")) {
                                            when (code.trim().removePrefix("0")) {
                                                "31" -> currentColor = errorColor
                                                "32" -> currentColor = cmdColor
                                                "33" -> currentColor = promptColor
                                                "34" -> currentColor = Color(0xFF569CD6)
                                            }
                                        }
                                    }
                                }
                                currentPos = match.range.last + 1
                            }
                            if (currentPos < terminalOutput.length) {
                                withStyle(SpanStyle(color = currentColor)) { append(terminalOutput.substring(currentPos)) }
                            }
                        }
                    }

                    LaunchedEffect(terminalOutput) { scrollState.scrollTo(scrollState.maxValue) }
                    Text(
                        text = annotatedTerminal,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = terminalInput,
                        onValueChange = { terminalInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ketik perintah...", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF00FF88)
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepRose,
                            unfocusedBorderColor = Color(0xFF333333),
                            cursorColor = Color(0xFF00FF88)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            val cmd = terminalInput.trim()
                            if (cmd.isNotEmpty()) {
                                terminalInput = ""
                                commandHistory.add(cmd)
                                historyIndex = -1
                                terminalOutput += "\n$ $cmd\n"
                                SshManager.getShell()?.sendCommand(cmd)
                            }
                        },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = DeepRose)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        proposedAction?.let { (title, message) ->
            ActionConfirmationDialog(
                title = title,
                message = message,
                onDismiss = { proposedAction = null },
                onConfirm = {
                    // Actual implementation would parse action here
                    proposedAction = null
                }
            )
        }
    }
}

class CodeHighlightTransformation(private val fileName: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightCode(text.text, fileName),
            OffsetMapping.Identity
        )
    }
}

private fun highlightCode(code: String, fileName: String): AnnotatedString {
    val defaultTextColor = Color(0xFFD4D4D4)
    val keywordColor = Color(0xFFC586C0) // Purple
    val typeColor = Color(0xFF4EC9B0)    // Teal
    val stringColor = Color(0xFFCE9178)  // Orange/Brown
    val commentColor = Color(0xFF6A9955) // Green
    val functionColor = Color(0xFFDCDCAA) // Yellowish
    val numberColor = Color(0xFFB5CEA8)  // Light Green
    val punctuationColor = Color(0xFF808080) // Gray
    val attributeColor = Color(0xFF9CDCFE) // Light Blue
    val keywordBlue = Color(0xFF569CD6)  // VS Code Blue

    return buildAnnotatedString {
        val keywords = listOf(
            "fun", "val", "var", "if", "else", "for", "while", "return", "class", "object", "import", "package",
            "def", "import", "as", "from", "in", "is", "not", "and", "or", "yield", "with", "elif",
            "function", "const", "let", "export", "default", "async", "await", "null", "true", "false",
            "case", "break", "continue", "try", "catch", "finally", "throw", "void", "static", "this"
        )
        
        val shellKeywords = listOf("echo", "exit", "sudo", "apt", "git", "docker", "python", "node", "npm", "sh", "bash", "ls", "cd", "mkdir", "rm", "cp", "mv")
        
        val patterns = mutableListOf<Pair<Regex, Color>>()
        patterns.add(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL) to commentColor)
        patterns.add(Regex("//.*|#.*") to commentColor)
        patterns.add(Regex("\".*?\"|'.*?'") to stringColor)
        patterns.add(Regex("\\b(${keywords.joinToString("|")})\\b") to keywordColor)
        patterns.add(Regex("\\b(${shellKeywords.joinToString("|")})\\b") to keywordBlue)
        patterns.add(Regex("\\b\\d+\\b") to numberColor)
        patterns.add(Regex("\\b[A-Z][a-zA-Z0-9_]*\\b") to typeColor)
        patterns.add(Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()") to functionColor)
        patterns.add(Regex("[{}()\\[\\].,;=+\\-*/%&|^!<>?:~]") to punctuationColor)
        patterns.add(Regex("[@$][a-zA-Z_][a-zA-Z0-9_]*") to attributeColor)

        val allMatches = patterns.flatMap { (regex, color) ->
            regex.findAll(code).map { it to color }
        }.sortedBy { it.first.range.first }

        var currentPos = 0
        for (match in allMatches) {
            val (result, color) = match
            val start = result.range.first
            val end = result.range.last + 1
            if (start < currentPos) continue
            
            if (start > currentPos) {
                withStyle(SpanStyle(color = defaultTextColor)) {
                    append(code.substring(currentPos, start))
                }
            }
            withStyle(SpanStyle(color = color)) {
                append(code.substring(start, end))
            }
            currentPos = end
        }
        if (currentPos < code.length) {
            withStyle(SpanStyle(color = defaultTextColor)) {
                append(code.substring(currentPos))
            }
        }
    }
}
