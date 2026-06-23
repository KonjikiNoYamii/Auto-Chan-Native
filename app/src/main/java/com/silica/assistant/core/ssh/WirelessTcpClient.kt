package com.silica.assistant.core.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

class WirelessTcpClient(
    private val host: String,
    private val port: Int = 9999
) : AutoCloseable {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val s = Socket(host, port)
            s.soTimeout = 3000
            s.setTcpNoDelay(true)
            socket = s
            writer = PrintWriter(s.getOutputStream(), true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Synchronized
    fun send(data: String) {
        try {
            writer?.println(data)
            writer?.flush()
        } catch (_: Exception) {}
    }

    fun isConnected(): Boolean =
        socket?.isConnected == true && socket?.isClosed == false

    fun disconnect() {
        try {
            writer?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        writer = null
    }

    override fun close() = disconnect()
}
