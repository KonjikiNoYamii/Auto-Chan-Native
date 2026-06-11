package com.silica.assistant.core.ssh

import com.jcraft.jsch.ChannelShell
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

class ShellSession(
    private val channel: ChannelShell
) : AutoCloseable {

    private val readerThread: Thread
    private var running = true
    private val outputListeners = mutableListOf<(String) -> Unit>()
    
    fun addOutputListener(listener: (String) -> Unit) {
        outputListeners.add(listener)
    }

    fun removeOutputListener(listener: (String) -> Unit) {
        outputListeners.remove(listener)
    }

    var onSudoPrompt: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val outputBuf = StringBuilder()
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val writeExecutor = Executors.newSingleThreadExecutor()

    init {
        channel.setPtyType("xterm-256color")
        channel.setPty(true)
        channel.connect()

        val inp: InputStream = channel.inputStream
        val out: OutputStream = channel.outputStream

        readerThread = Thread {
            val buf = ByteArray(8192)
            try {
                while (running) {
                    val len = inp.read(buf)
                    if (len <= 0) break
                    val raw = String(buf, 0, len, Charsets.UTF_8)
                    val cleaned = normalizeOutput(raw)
                    outputBuf.append(cleaned)
                    outputListeners.forEach { it.invoke(cleaned) }

                    if (cleaned.contains("[sudo]", ignoreCase = true) ||
                        cleaned.contains("password for", ignoreCase = true) ||
                        cleaned.trim().lowercase() == "password:"
                    ) {
                        onSudoPrompt?.invoke()
                    }
                }
            } catch (e: Exception) {
                if (running) onError?.invoke("Shell error: ${e.message}")
            }
        }.apply { start() }

        outputStream = out
        inputStream = inp
    }

    fun sendCommand(cmd: String) {
        writeExecutor.submit {
            try {
                outputStream?.write((cmd + "\n").toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                onError?.invoke("Gagal kirim: ${e.message}")
            }
        }
    }

    fun sendPassword(pwd: String) {
        sendCommand(pwd)
    }

    fun interrupt() {
        writeExecutor.submit {
            try {
                outputStream?.write(0x03)
                outputStream?.flush()
            } catch (_: Exception) {}
        }
    }

    fun injectOutput(text: String) {
        outputBuf.append(text)
        outputListeners.forEach { it.invoke(text) }
    }

    fun getFullOutput(): String = outputBuf.toString()

    override fun close() {
        running = false
        try { writeExecutor.shutdownNow() } catch (_: Exception) {}
        try { channel.disconnect() } catch (_: Exception) {}
        try { readerThread.join(2000) } catch (_: Exception) {}
    }

    private fun normalizeOutput(text: String): String {
        // 1. Aggressively remove OSC sequences (title setting)
        // \u001B]... until \u0007 (BEL) or \u001B\\ (ST)
        var filtered = text.replace(Regex("\\u001B\\][0-9];.*?(\\u0007|\\u001B\\\\)"), "")
        
        // 2. Remove remaining BEL and other stray control characters
        // We keep \n (10), \r (13), and \t (9)
        // We also KEEP \u001B (27) for the UI parser to handle CSI colors
        val sb = StringBuilder()
        for (char in filtered) {
            val code = char.code
            if (code == 27 || code == 10 || code == 13 || code == 9 || code >= 32) {
                sb.append(char)
            }
        }
        
        return sb.toString()
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }
}
