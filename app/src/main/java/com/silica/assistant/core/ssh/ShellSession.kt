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
    var onOutput: ((String) -> Unit)? = null
    var onSudoPrompt: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val outputBuf = StringBuilder()
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val writeExecutor = Executors.newSingleThreadExecutor()

    init {
        channel.setPtyType("xterm")
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
                    val cleaned = stripAnsi(raw)
                    outputBuf.append(cleaned)
                    onOutput?.invoke(cleaned)

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

    fun getFullOutput(): String = outputBuf.toString()

    override fun close() {
        running = false
        try { writeExecutor.shutdownNow() } catch (_: Exception) {}
        try { channel.disconnect() } catch (_: Exception) {}
        try { readerThread.join(2000) } catch (_: Exception) {}
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")
            .replace("\u0007", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }
}
