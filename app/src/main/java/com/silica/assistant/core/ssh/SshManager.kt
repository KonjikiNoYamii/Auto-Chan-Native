package com.silica.assistant.core.ssh

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

object SshManager {
    private var session: Session? = null
    private var currentConnection: SshConnection? = null
    var homePath: String = "/"

    // known hosts tracking
    var knownHostsMatch: Boolean = true
    private var connectionId: String = ""

    private fun getHostPrefs(context: Context) =
        context.getSharedPreferences("ssh_known_hosts", Context.MODE_PRIVATE)

    private fun isHostKnown(context: Context, host: String, port: Int): Boolean {
        return getHostPrefs(context).getBoolean("${host}:${port}", false)
    }

    private fun markHostKnown(context: Context, host: String, port: Int) {
        getHostPrefs(context).edit().putBoolean("${host}:${port}", true).apply()
    }

    private var monitorThread: Thread? = null
    private var monitorChannel: ChannelExec? = null
    private var monitorCallback: ((String) -> Unit)? = null

    fun startMonitor(onData: (String) -> Unit): Boolean {
        stopMonitor()
        val s = session ?: return false
        return try {
            val channel = s.openChannel("exec") as ChannelExec
            channel.setCommand("while true; do echo \"=== REFRESH ===\"; uptime -p 2>/dev/null || echo '?'; echo \"---\"; free -h 2>/dev/null | head -3; echo \"---\"; df -h / 2>/dev/null | tail -1; sleep 3; done")
            channel.connect()
            monitorChannel = channel
            monitorCallback = onData
            monitorThread = Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(channel.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        monitorCallback?.invoke(line!!)
                    }
                } catch (_: Exception) { }
            }.apply { start() }
            true
        } catch (_: Exception) { false }
    }

    fun stopMonitor() {
        monitorCallback = null
        try { monitorChannel?.disconnect() } catch (_: Exception) { }
        monitorChannel = null
        monitorThread = null
    }

    fun generateKeyPair(context: Context): String? {
        return try {
            val kp = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
            val pubStream = java.io.ByteArrayOutputStream()
            val privStream = java.io.ByteArrayOutputStream()
            kp.writePublicKey(pubStream, "silica@assistant")
            kp.writePrivateKey(privStream)
            kp.dispose()
            val pub = pubStream.toString()
            val priv = privStream.toString()

            File(context.filesDir, "ssh_private").writeText(priv)
            File(context.filesDir, "ssh_public.pub").writeText(pub)
            pub
        } catch (e: Exception) { null }
    }

    fun getSavedPublicKey(context: Context): String? {
        val f = File(context.filesDir, "ssh_public.pub")
        return if (f.exists()) f.readText() else null
    }

    fun hasSavedKey(context: Context): Boolean =
        File(context.filesDir, "ssh_private").exists()

    fun connect(connection: SshConnection, context: Context? = null): Result<Unit> = runCatching {
        disconnect()
        val jsch = JSch()

        val keyPrivate = when {
            connection.authType == AuthType.KEY && connection.keyPrivate.isNotBlank() -> connection.keyPrivate
            connection.password.isBlank() && context != null && hasSavedKey(context) ->
                File(context.filesDir, "ssh_private").readText()
            else -> null
        }
        if (keyPrivate != null) {
            jsch.addIdentity(
                "sshkey",
                keyPrivate.toByteArray(),
                null,
                null
            )
        }

        val s = jsch.getSession(connection.username, connection.host, connection.port)
        if (connection.password.isNotBlank()) {
            s.setPassword(connection.password)
        }

        knownHostsMatch = true
        connectionId = "${connection.host}:${connection.port}"

        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("ServerAliveInterval", "15")
        s.setConfig("ServerAliveCountMax", "3")
        s.connect(10000)

        // Tandai host sebagai pernah terhubung (untuk tracking "Lupakan")
        if (context != null && !isHostKnown(context, connection.host, connection.port)) {
            markHostKnown(context, connection.host, connection.port)
        }

        session = s
        currentConnection = connection
        homePath = resolveHome()
    }

    private fun resolveHome(): String {
        return try {
            val channel = session?.openChannel("exec") as ChannelExec
            channel.setCommand("echo ${'$'}HOME")
            val output = ByteArrayOutputStream()
            channel.outputStream = output
            channel.connect()
            while (!channel.isClosed) { Thread.sleep(100) }
            channel.disconnect()
            output.toString().trim()
        } catch (e: Exception) {
            "/"
        }
    }

    fun disconnect() {
        stopMonitor()
        closeShell()
        session?.disconnect()
        session = null
        currentConnection = null
    }

    fun isConnected(): Boolean = session?.isConnected == true

    private var shellSession: ShellSession? = null

    fun openShell(): Result<ShellSession> = runCatching {
        closeShell()
        val s = session ?: throw Exception("Not connected")
        val channel = s.openChannel("shell") as ChannelShell
        val shell = ShellSession(channel)
        shellSession = shell
        shell
    }

    fun closeShell() {
        try { shellSession?.close() } catch (_: Exception) {}
        shellSession = null
    }

    fun isShellActive(): Boolean = shellSession != null

    fun getShell(): ShellSession? = shellSession

    fun getCurrentConnection(): SshConnection? = currentConnection

    fun executeCommand(command: String): Result<String> = runCatching {
        val s = session ?: throw Exception("Not connected")
        val channel = s.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        channel.outputStream = output
        channel.setExtOutputStream(error)
        channel.connect()
        while (!channel.isClosed) {
            Thread.sleep(100)
        }
        channel.disconnect()
        val out = output.toString().trimEnd()
        val err = error.toString().trimEnd()
        if (err.isNotEmpty()) "$out\n$err" else out
    }

    fun listFiles(path: String): Result<List<SshFile>> = runCatching {
        val s = session ?: throw Exception("Not connected")
        val channel = s.openChannel("exec") as ChannelExec
        val cmd = if (path == "/") "ls -la /" else "ls -la '$path'"
        channel.setCommand(cmd)
        val output = ByteArrayOutputStream()
        channel.outputStream = output
        channel.connect()
        while (!channel.isClosed) {
            Thread.sleep(100)
        }
        channel.disconnect()
        val lines = output.toString().lines().filter { it.isNotBlank() }
        lines.drop(1).mapNotNull { line -> parseLsLine(line, path) }
    }

    private fun parseLsLine(line: String, parentPath: String): SshFile? {
        val parts = line.split("\\s+".toRegex())
        if (parts.size < 9) return null
        val perms = parts[0]
        val isDir = perms.startsWith("d")
        val size = parts[4].toLongOrNull() ?: 0L
        val name = parts.drop(8).joinToString(" ")
        if (name == "." || name == "..") return null
        val sep = if (parentPath.endsWith("/")) "" else "/"
        return SshFile(
            name = name,
            path = "$parentPath$sep$name",
            isDirectory = isDir,
            size = size,
            permissions = perms
        )
    }

    fun uploadFile(localPath: String, remotePath: String): Result<Unit> = runCatching {
        val s = session ?: throw Exception("Not connected")
        val channel = s.openChannel("sftp") as ChannelSftp
        try {
            channel.connect()
            FileInputStream(File(localPath)).use { input ->
                channel.put(input, remotePath)
            }
        } finally {
            try { channel.disconnect() } catch (_: Exception) {}
        }
    }

    fun downloadFile(remotePath: String, localPath: String): Result<Unit> = runCatching {
        val s = session ?: throw Exception("Not connected")
        val channel = s.openChannel("sftp") as ChannelSftp
        try {
            channel.connect()
            FileOutputStream(File(localPath)).use { output ->
                channel.get(remotePath, output)
            }
        } finally {
            try { channel.disconnect() } catch (_: Exception) {}
        }
    }

    fun clearKnownHost(context: Context, host: String, portNum: Int) {
        getHostPrefs(context).edit().remove("${host}:${portNum}").apply()
        knownHostsMatch = true
    }

    fun clearAllKnownHosts(context: Context) {
        getHostPrefs(context).edit().clear().apply()
        knownHostsMatch = true
    }

    fun getConnectionId(): String = connectionId

    // =========================
    // SAVED CONNECTION
    // =========================
    private fun getConnPrefs(context: Context) =
        context.getSharedPreferences("ssh_saved_connection", Context.MODE_PRIVATE)

    fun saveConnection(context: Context, conn: SshConnection, savePassword: Boolean = false) {
        getConnPrefs(context).edit().apply {
            putString("host", conn.host)
            putInt("port", conn.port)
            putString("username", conn.username)
            if (savePassword && conn.password.isNotBlank()) {
                putString("password", conn.password)
            } else {
                remove("password")
            }
            apply()
        }
    }

    fun loadSavedConnection(context: Context): SshConnection? {
        val prefs = getConnPrefs(context)
        val host = prefs.getString("host", null) ?: return null
        val port = prefs.getInt("port", 22)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        return SshConnection(
            name = "$username@$host",
            host = host,
            port = port,
            username = username,
            password = password
        )
    }

    fun hasSavedConnection(context: Context): Boolean {
        return getConnPrefs(context).contains("host")
    }

    fun clearSavedConnection(context: Context) {
        getConnPrefs(context).edit().clear().apply()
    }
}
