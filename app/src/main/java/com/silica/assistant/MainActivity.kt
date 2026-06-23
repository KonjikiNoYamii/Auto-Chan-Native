package com.silica.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.silica.assistant.core.llm.LlmClient
import com.silica.assistant.core.voice.VoiceManager
import com.silica.assistant.ui.MainScreen
import com.silica.assistant.ui.theme.SilicaTheme
import com.silica.assistant.ui.ssh.WirelessModeManager
import kotlinx.coroutines.launch
import com.silica.assistant.ui.state.Screen
import com.silica.assistant.core.llm.WaifuNotifier
import com.silica.assistant.core.system.SoundManager
import com.silica.assistant.ui.ssh.WirelessInputMapper
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var lastMouseX = -1f
    private var lastMouseY = -1f
    private var pointerCaptureView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VoiceManager.init(this)
        SoundManager.init(this)

        lifecycleScope.launch {
            LlmClient.startPeriodicHealthCheck()
            WaifuNotifier.showTimeBasedGreeting()
        }

        lifecycleScope.launch {
            WirelessModeManager.isActive.collect { active ->
                if (active) enablePointerCapture()
                else disablePointerCapture()
            }
        }

        val openScreen = intent.getStringExtra("OPEN_SCREEN")

        setContent {
            SilicaTheme(darkTheme = false) {
                MainScreen(initialScreen = if (openScreen == "CHAT") Screen.Chat else Screen.Main)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && WirelessModeManager.isActive.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pointerCaptureView?.let { v ->
                v.post {
                    v.requestFocus()
                    try { v.requestPointerCapture() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun enablePointerCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                window?.decorView?.pointerIcon =
                    PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL)
            }
            return
        }
        val view = View(this)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnCapturedPointerListener { _, event ->
            WirelessModeManager.onMouseEvent()
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x
                    val dy = event.y
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        WirelessModeManager.onMouseDx(dx.roundToInt(), dy.roundToInt())
                    }
                }
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    WirelessModeManager.onMouseClick(event.actionButton)
                }
            }
            true
        }
        addContentView(view, ViewGroup.LayoutParams(1, 1))
        view.post {
            view.requestFocus()
            try { view.requestPointerCapture() } catch (_: Exception) {}
        }
        pointerCaptureView = view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window?.decorView?.pointerIcon =
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL)
        }
    }

    private fun disablePointerCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pointerCaptureView?.post {
                try { pointerCaptureView?.releasePointerCapture() } catch (_: Exception) {}
            }
        }
        pointerCaptureView?.let { v ->
            val parent = v.parent as? ViewGroup
            parent?.removeView(v)
        }
        pointerCaptureView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window?.decorView?.pointerIcon = null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (WirelessModeManager.isActive.value && WirelessModeManager.isTcpConnected() && event != null) {
            val keyCode = event.keyCode
            val mapped = WirelessInputMapper.keyCodeToXdotool(keyCode)
            if (mapped != null && mapped.isNotEmpty()) {
                WirelessModeManager.onKeyEvent(event)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (WirelessModeManager.isActive.value && WirelessModeManager.isTcpConnected() && event != null && event.source == InputDevice.SOURCE_MOUSE) {
            when (event.actionMasked) {
                MotionEvent.ACTION_SCROLL -> {
                    WirelessModeManager.onMouseEvent()
                    val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    WirelessModeManager.onMouseScroll(vscroll)
                }
                else -> {
                    WirelessModeManager.onMouseEvent()
                    val x = event.x
                    val y = event.y
                    if (lastMouseX >= 0 && lastMouseY >= 0) {
                        val dx = (x - lastMouseX).roundToInt()
                        val dy = (y - lastMouseY).roundToInt()
                        if (dx != 0 || dy != 0) {
                            WirelessModeManager.onMouseDx(dx, dy)
                        }
                    }
                    lastMouseX = x
                    lastMouseY = y
                    if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
                        WirelessModeManager.onMouseClick(event.actionButton)
                    }
                }
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
