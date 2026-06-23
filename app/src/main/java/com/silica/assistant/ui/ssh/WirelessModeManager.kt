package com.silica.assistant.ui.ssh

import android.content.Context
import android.os.PowerManager
import android.view.InputDevice
import android.view.KeyEvent
import com.silica.assistant.core.ssh.WirelessTcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WirelessStatus { Disconnected, Connecting, Connected, Error }

object WirelessModeManager {

    private var client: WirelessTcpClient? = null
    private var healthJob: Job? = null
    private var sendJob: Job? = null
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null

    var host: String = ""

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _status = MutableStateFlow(WirelessStatus.Disconnected)
    val status: StateFlow<WirelessStatus> = _status.asStateFlow()

    private val _kbdDetected = MutableStateFlow(false)
    val kbdDetected: StateFlow<Boolean> = _kbdDetected.asStateFlow()

    private val _mouseDetected = MutableStateFlow(false)
    val mouseDetected: StateFlow<Boolean> = _mouseDetected.asStateFlow()

    private var keysDown = mutableSetOf<String>()

    fun isTcpConnected(): Boolean = client?.isConnected() == true

    fun checkConnectedDevices() {
        _kbdDetected.value = false
        _mouseDetected.value = false
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (dev.sources and InputDevice.SOURCE_KEYBOARD != 0) {
                _kbdDetected.value = true
            }
            if (dev.sources and InputDevice.SOURCE_MOUSE != 0) {
                _mouseDetected.value = true
            }
        }
    }

    private fun acquireWakeLock(context: Context) {
        if (wakeLock != null) return
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Silica:WirelessInput")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun sendCmd(cmd: String) {
        val c = client ?: return
        sendJob = scope?.launch(Dispatchers.IO) {
            c.send(cmd)
        }
    }

    private fun releaseAllKeys() {
        val held = keysDown.toList()
        keysDown.clear()
        held.forEach { sendCmd("xdotool keyup $it") }
    }

    fun start(context: Context, coroutineScope: CoroutineScope) {
        if (_isActive.value) return
        if (host.isBlank()) {
            _status.value = WirelessStatus.Error
            return
        }
        _isActive.value = true
        _kbdDetected.value = false
        _mouseDetected.value = false
        checkConnectedDevices()
        _status.value = WirelessStatus.Connecting
        scope = coroutineScope
        acquireWakeLock(context)

        coroutineScope.launch(Dispatchers.IO) {
            val c = WirelessTcpClient(host)
            val result = c.connect()
            result.onSuccess {
                client = c
                _status.value = WirelessStatus.Connected
                startHealthCheck(coroutineScope)
            }.onFailure {
                _status.value = WirelessStatus.Error
                _isActive.value = false
                releaseWakeLock()
                c.close()
            }
        }
    }

    fun retry(context: Context, coroutineScope: CoroutineScope) {
        if (_status.value != WirelessStatus.Error) return
        healthJob?.cancel()
        healthJob = null
        releaseAllKeys()
        client?.disconnect()
        client = null
        _isActive.value = true
        _kbdDetected.value = false
        _mouseDetected.value = false
        checkConnectedDevices()
        _status.value = WirelessStatus.Connecting
        scope = coroutineScope
        acquireWakeLock(context)

        coroutineScope.launch(Dispatchers.IO) {
            val c = WirelessTcpClient(host)
            val result = c.connect()
            result.onSuccess {
                client = c
                _status.value = WirelessStatus.Connected
                startHealthCheck(coroutineScope)
            }.onFailure {
                _status.value = WirelessStatus.Error
                _isActive.value = false
                releaseWakeLock()
                c.close()
            }
        }
    }

    fun stop() {
        healthJob?.cancel()
        healthJob = null
        sendJob?.cancel()
        sendJob = null
        releaseAllKeys()
        client?.disconnect()
        client = null
        releaseWakeLock()
        _kbdDetected.value = false
        _mouseDetected.value = false
        _isActive.value = false
        _status.value = WirelessStatus.Disconnected
    }

    fun onKeyEvent(event: KeyEvent) {
        if (!_isActive.value || !isTcpConnected()) return
        _kbdDetected.value = true
        val keyCode = event.keyCode
        val mapped = WirelessInputMapper.keyCodeToXdotool(keyCode) ?: return
        if (mapped.isEmpty()) return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    keysDown.add(mapped)
                    sendCmd("xdotool keydown $mapped")
                }
            }
            KeyEvent.ACTION_UP -> {
                keysDown.remove(mapped)
                sendCmd("xdotool keyup $mapped")
            }
        }
    }

    fun onMouseEvent() {
        if (!_isActive.value || !isTcpConnected()) return
        _mouseDetected.value = true
    }

    fun onMouseDx(dx: Int, dy: Int) {
        if (!_isActive.value || !isTcpConnected()) return
        if (dx != 0 || dy != 0) {
            sendCmd("xdotool mousemove_relative -- $dx $dy")
        }
    }

    fun onMouseClick(button: Int) {
        if (!_isActive.value || !isTcpConnected()) return
        val btn = WirelessInputMapper.getButtonName(button) ?: "1"
        sendCmd("xdotool click $btn")
    }

    fun onMouseScroll(amount: Float) {
        if (!_isActive.value || !isTcpConnected()) return
        val btn = WirelessInputMapper.scrollToXdotool(amount)
        val times = kotlin.math.abs(amount).toInt().coerceIn(1, 10)
        repeat(times) { sendCmd("xdotool click $btn") }
    }

    private fun startHealthCheck(coroutineScope: CoroutineScope) {
        healthJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10_000)
                if (!isTcpConnected()) {
                    releaseAllKeys()
                    _status.value = WirelessStatus.Error
                    break
                }
                client?.send("#ping")
            }
            if (!isTcpConnected()) {
                _isActive.value = false
            }
        }
    }
}
