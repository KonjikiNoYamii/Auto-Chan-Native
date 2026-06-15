package com.silica.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.silica.assistant.core.overlay.OverlayEventBus

class SilicaAccessibilityService : AccessibilityService() {

    private var rootNode: AccessibilityNodeInfo? = null
    private var lastEventTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        OverlayEventBus.accessibilityService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventTime = System.currentTimeMillis()
        rootNode?.recycle()
        rootNode = rootInActiveWindow
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        rootNode?.recycle()
        rootNode = null
        OverlayEventBus.accessibilityService = null
    }

    fun getScreenText(): String {
        var node = rootInActiveWindow
        if (node == null) {
            android.util.Log.w("SilicaAcc", "rootInActiveWindow is null, trying cached rootNode")
            node = rootNode
        }
        if (node == null) {
            android.util.Log.w("SilicaAcc", "rootNode is also null")
            return ""
        }
        val text = buildString {
            collectText(node)
        }
        node.recycle()
        val result = text.trim().take(500)
        android.util.Log.d("SilicaAcc", "getScreenText: '${result.take(100)}' (${result.length} chars)")
        return result
    }

    private fun StringBuilder.collectText(node: AccessibilityNodeInfo) {
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrBlank()) {
            if (isNotEmpty()) append(" | ")
            append(t)
        }
        val cd = node.contentDescription?.toString()?.trim()
        if (!cd.isNullOrBlank()) {
            if (isNotEmpty()) append(" | ")
            append(cd)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectText(child)
                child.recycle()
            }
        }
    }

    fun findAndClick(text: String): Boolean {
        val node = rootInActiveWindow ?: rootNode ?: return false
        val normalized = text.lowercase().trim()
        val matches = mutableListOf<AccessibilityNodeInfo>()

        findNodesByText(node, normalized, matches)

        for (match in matches) {
            val clickable = findClickableAncestor(match)
            if (clickable != null) {
                val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickable.recycle()
                // Clean up all found nodes
                for (m in matches) m.recycle()
                if (node != rootNode) node.recycle()
                return success
            }
        }
        
        // Clean up if no clickable found
        for (m in matches) m.recycle()
        if (node != rootNode) node.recycle()
        return false
    }

    private fun findNodesByText(node: AccessibilityNodeInfo, text: String, results: MutableList<AccessibilityNodeInfo>) {
        val nodeText = node.text?.toString()?.lowercase()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        if (text in nodeText || text in nodeDesc) {
            results.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByText(child, text, results)
            child.recycle()
        }
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return null
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performScrollDown(): Boolean {
        return swipeFractional(0.5f, 0.7f, 0.5f, 0.3f)
    }

    fun performScrollUp(): Boolean {
        return swipeFractional(0.5f, 0.3f, 0.5f, 0.7f)
    }

    private fun dispatchWithTouchableToggle(
        path: Path, durationMs: Long,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        OverlayEventBus.setOverlayTouchable?.invoke(false)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    OverlayEventBus.setOverlayTouchable?.invoke(true)
                    onComplete?.invoke()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    OverlayEventBus.setOverlayTouchable?.invoke(true)
                }
            }, android.os.Handler(mainLooper))
            true
        } catch (e: Exception) {
            OverlayEventBus.setOverlayTouchable?.invoke(true)
            android.util.Log.e("SilicaAcc", "Gesture failed: ${e.message}", e)
            false
        }
    }

    fun clickAt(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        return dispatchWithTouchableToggle(path, 100)
    }

    fun swipeAbsolute(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 400): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return dispatchWithTouchableToggle(path, durationMs) {
            android.util.Log.d("SilicaAcc", "swipeAbsolute done ($startX,$startY→$endX,$endY)")
        }
    }

    fun longPressAt(x: Int, y: Int, durationMs: Long = 600): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        return dispatchWithTouchableToggle(path, durationMs)
    }

    fun swipeFractional(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 400): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        return swipeAbsolute(
            (fromX * width).toInt(), (fromY * height).toInt(),
            (toX * width).toInt(), (toY * height).toInt(),
            durationMs
        )
    }

    fun typeText(text: String): Boolean {
        return findAndTypeInternal(text, rootInActiveWindow ?: rootNode)
    }

    private fun findAndTypeInternal(text: String, root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("silica_type", text))

        // Try focused node first
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            focused.recycle()
            return success
        }
        focused?.recycle()

        // Fall back to findEditableNode
        val editable = findEditableNode(root)
        if (editable != null) {
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val success = editable.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            editable.recycle()
            return success
        }
        return false
    }

    fun findAndType(text: String): Boolean {
        return findAndTypeInternal(text, rootInActiveWindow ?: rootNode)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.className?.toString()?.lowercase()?.contains("edittext") == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    fun waitForText(text: String, timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val screenText = getScreenText()
            if (screenText.contains(text, ignoreCase = true)) return true
            try { Thread.sleep(200) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
}
