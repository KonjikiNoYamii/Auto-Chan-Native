package com.silica.assistant.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
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
        var node = rootInActiveWindow
        if (node == null) node = rootNode
        val scrollable = if (node != null) findScrollableNode(node) else null
        return if (scrollable != null) {
            val result = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            scrollable.recycle()
            result
        } else false
    }

    fun performScrollUp(): Boolean {
        var node = rootInActiveWindow
        if (node == null) node = rootNode
        val scrollable = if (node != null) findScrollableNode(node) else null
        return if (scrollable != null) {
            val result = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            scrollable.recycle()
            result
        } else false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
}
