package com.silica.assistant.core

import com.silica.assistant.core.ssh.SshManager

object AiExecutionEngine {

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

    fun executeViaSsh(code: String, language: String): Result<String> {
        return try {
            val ext = when (language.lowercase()) {
                "python", "py" -> ".py"
                "kotlin" -> ".kt"
                "java" -> ".java"
                "javascript", "js" -> ".js"
                "typescript", "ts" -> ".ts"
                "bash", "sh" -> ".sh"
                "ruby", "rb" -> ".rb"
                "go" -> ".go"
                "cpp", "c++", "c" -> ".cpp"
                else -> ".txt"
            }
            val remotePath = "/tmp/silica_task_${System.currentTimeMillis()}$ext"

            SshManager.saveFileContent(remotePath, code).getOrThrow()

            val runner = when (language.lowercase()) {
                "python", "py" -> "python3"
                "kotlin" -> "kotlin"
                "java" -> "java"
                "javascript", "js" -> "node"
                "typescript", "ts" -> "npx ts-node"
                "bash", "sh" -> "bash"
                "ruby", "rb" -> "ruby"
                "go" -> "go run"
                else -> null
            }

            val output = if (runner != null) {
                SshManager.executeCommand("cd /tmp && $runner '$remotePath'").getOrThrow()
            } else {
                ""
            }

            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
