package com.silica.assistant.core.ssh

data class SshFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val permissions: String = ""
)
