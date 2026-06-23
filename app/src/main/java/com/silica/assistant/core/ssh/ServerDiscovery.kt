package com.silica.assistant.core.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.NetworkInterface

class ServerDiscovery(private val scope: CoroutineScope) {

    data class DiscoveredServer(val ip: String)

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private var job: Job? = null

    fun start(timeoutMs: Long = 3000) {
        stop()
        _servers.value = emptyList()
        job = scope.launch(Dispatchers.IO) {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs.toInt()
            socket.broadcast = true

            val requestBytes = "SILICA_DISCOVER".toByteArray()

            try {
                val broadcast = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(requestBytes, requestBytes.size, broadcast, 9998)
                socket.send(packet)
            } catch (_: Exception) {}

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces?.hasMoreElements() == true) {
                    val intf = interfaces.nextElement()
                    if (intf.isLoopback || !intf.isUp) continue
                    for (addr in intf.interfaceAddresses) {
                        val broadcastAddr = addr.broadcast ?: continue
                        val packet = DatagramPacket(requestBytes, requestBytes.size, broadcastAddr, 9998)
                        socket.send(packet)
                    }
                }
            } catch (_: Exception) {}

            val buf = ByteArray(256)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    if (response == "SILICA_SERVER") {
                        val ip = packet.address.hostAddress ?: continue
                        val current = _servers.value
                        if (current.none { it.ip == ip }) {
                            _servers.value = current + DiscoveredServer(ip)
                            delay(100)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                    break
                }
            }
            socket.close()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
