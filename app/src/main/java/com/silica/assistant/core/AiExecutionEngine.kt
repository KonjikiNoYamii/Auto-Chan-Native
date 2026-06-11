package com.silica.assistant.core

import com.silica.assistant.core.ssh.SshManager

object AiExecutionEngine {

    const val PROJECTS_BASE = "SilicaProjects"

    sealed class Action {
        data class CreateFolder(val path: String) : Action()
        data class CreateFile(val path: String, val code: String) : Action()
        data class RunCommand(val command: String) : Action()
    }

    data class ParseResult(
        val actions: List<Action>,
        val resultText: String
    )

    data class CodeBlock(
        val language: String,
        val code: String
    )

    fun extractCodeBlocks(text: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        val regex = Regex("```(\\w*)\\s*\\n([\\s\\S]*?)```")
        for (match in regex.findAll(text)) {
            val lang = match.groupValues[1].ifBlank { "text" }
            val code = match.groupValues[2].trim()
            if (code.isNotBlank()) {
                blocks.add(CodeBlock(lang, code))
            }
        }
        return blocks
    }

    fun parseActions(response: String): ParseResult? {
        val fullText = response.trim()
        val actions = mutableListOf<Action>()
        var resultText = "Selesai~"

        val actionRegex = Regex("ACTION:\\s*(\\w+)", RegexOption.IGNORE_CASE)
        val fileRegex = Regex("FILE:\\s*(.+)", RegexOption.IGNORE_CASE)
        val codeRegex = Regex("```(\\w*)\\s*\\n([\\s\\S]*?)```")
        val commandRegex = Regex("COMMAND:\\s*(.+)", RegexOption.IGNORE_CASE)
        val resultLineRegex = Regex("RESULT:\\s*(.+)", RegexOption.IGNORE_CASE)

        val lines = fullText.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val actionMatch = actionRegex.find(line)
            if (actionMatch != null) {
                val actionType = actionMatch.groupValues[1].lowercase()
                i++

                // Find FILE: (optional for run_command)
                var filePath = ""
                while (i < lines.size) {
                    val fl = lines[i].trim()
                    val fileMatch = fileRegex.find(fl)
                    if (fileMatch != null) {
                        filePath = fileMatch.groupValues[1].trim()
                        // Convert relative paths to full project path
                        filePath = resolvePath(filePath)
                        i++
                    }
                    break
                }

                // If next line starts with COMMAND, skip PREV block
                if (i < lines.size) {
                    val cmdCheck = lines[i].trim()
                    val cmdMatch = commandRegex.find(cmdCheck)
                    if (cmdMatch != null) {
                        // run_command: filePath is the command
                        actions.add(Action.RunCommand(cmdMatch.groupValues[1].trim()))
                        i++
                        continue
                    }
                }

                // Collect CODE block (#### or ``` blocks)
                val codeBlock = StringBuilder()
                while (i < lines.size) {
                    val cl = lines[i]
                    if (cl.trim().startsWith("```")) {
                        // Found ``` maybe with lang
                        val codeMatch = codeRegex.find(cl + "\n" + lines.drop(i + 1).joinToString("\n"))
                        if (codeMatch != null) {
                            codeBlock.append(codeMatch.groupValues[2].trim())
                            val contentLines = codeMatch.groupValues[2].split("\n")
                            i += 1 + contentLines.size + 1 // opening ``` + content + closing ```
                            continue
                        }
                        i++
                        continue
                    }
                    val cmdMatch = commandRegex.find(cl.trim())
                    val resMatch = resultLineRegex.find(cl.trim())
                    val actMatch = actionRegex.find(cl.trim())
                    if (cmdMatch != null) {
                        actions.add(Action.RunCommand(cmdMatch.groupValues[1].trim()))
                        i++
                        break
                    }
                    if (resMatch != null) {
                        resultText = resMatch.groupValues[1].trim()
                        i++
                        continue
                    }
                    if (actMatch != null) {
                        // Next action — don't consume, break to let outer loop handle it
                        break
                    }
                    // Plain text line in code block
                    if (codeBlock.isNotEmpty() || cl.trim().isNotEmpty()) {
                        if (cl.trim().isNotEmpty() && !cl.trim().startsWith("CODE:")) {
                            codeBlock.appendLine(cl.trimEnd())
                        }
                    }
                    i++
                }

                when (actionType) {
                    "create_folder" -> {
                        if (filePath.isNotBlank()) {
                            actions.add(Action.CreateFolder(filePath))
                        }
                    }
                    "create_file" -> {
                        val code = codeBlock.toString().trim()
                        if (filePath.isNotBlank() && code.isNotEmpty()) {
                            actions.add(Action.CreateFile(filePath, code))
                        } else if (code.isNotEmpty()) {
                            // No file path specified, infer from context
                            actions.add(Action.CreateFile(PROJECTS_BASE + "/project/file.txt", code))
                        }
                    }
                    "run_command" -> {
                        val cmd = filePath.ifBlank {
                            codeBlock.toString().trim()
                        }
                        if (cmd.isNotBlank()) {
                            actions.add(Action.RunCommand(cmd))
                        }
                    }
                }
                continue
            }
            i++
        }

        // Fallback: extract raw code blocks into files
        if (actions.isEmpty()) {
            val blocks = extractCodeBlocks(fullText)
            if (blocks.isNotEmpty()) {
                val projectName = "project"
                actions.add(Action.CreateFolder("$PROJECTS_BASE/$projectName"))
                for ((i, b) in blocks.withIndex()) {
                    val ext = when (b.language.lowercase()) {
                        "python", "py" -> ".py"; "bash", "sh" -> ".sh"
                        "javascript", "js" -> ".js"; "typescript", "ts" -> ".ts"
                        "kotlin" -> ".kt"; "java" -> ".java"
                        "ruby", "rb" -> ".rb"; "go" -> ".go"
                        else -> ".txt"
                    }
                    val fileName = if (blocks.size == 1) "main$ext" else "file${i + 1}$ext"
                    actions.add(Action.CreateFile("$PROJECTS_BASE/$projectName/$fileName", b.code))
                }
            }
        }

        return ParseResult(actions, resultText)
    }

    private fun resolvePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("/")) return trimmed
        if (trimmed.startsWith("~")) return trimmed.replaceFirst("~", SshManager.homePath)
        val base = PROJECTS_BASE
        if (trimmed.startsWith(base)) return trimmed
        return "$base/$trimmed"
    }

    fun executeActions(actions: List<Action>): Result<String> {
        return try {
            val outputs = mutableListOf<String>()

            for (action in actions) {
                when (action) {
                    is Action.CreateFolder -> {
                        val cmd = "mkdir -p '${action.path}'"
                        SshManager.executeCommand(cmd).getOrThrow()
                        outputs.add("📁 ${action.path}")
                    }
                    is Action.CreateFile -> {
                        // Ensure parent folder exists
                        val parentCmd = "mkdir -p '${action.path.substringBeforeLast("/")}'"
                        SshManager.executeCommand(parentCmd).getOrThrow()
                        SshManager.saveFileContent(action.path, action.code).getOrThrow()
                        outputs.add("📝 ${action.path}")
                    }
                    is Action.RunCommand -> {
                        val result = SshManager.executeCommand(action.command).getOrThrow()
                        outputs.add("💻 ${action.command}")
                        if (result.isNotBlank()) {
                            outputs.addAll(result.take(500).split("\n").map { "  $it" })
                        }
                    }
                }
            }

            Result.success(outputs.joinToString("\n"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
