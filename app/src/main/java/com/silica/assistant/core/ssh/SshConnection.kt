package com.silica.assistant.core.ssh

import java.util.UUID

data class SshConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val keyPrivate: String = "",
    val keyPassphrase: String = ""
)

enum class AuthType { PASSWORD, KEY }
