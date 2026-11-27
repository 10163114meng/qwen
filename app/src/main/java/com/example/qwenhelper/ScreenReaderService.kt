package com.example.qwenhelper

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class ScreenReaderService : AccessibilityService() {

    companion object {
        var instance: ScreenReaderService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun readCurrentScreenChat(screenWidth: Int, screenHeight: Int): String {
        val targetRoot = findWeChatWindowRoot() ?: return ""
        val messages = mutableListOf<String>()
        val topLimit = (screenHeight * 0.10).toInt()
        val bottomLimit = (screenHeight * 0.86).toInt()

        traverseTextNodes(targetRoot, screenWidth, topLimit, bottomLimit, messages)

        val recent = if (messages.size > 8) messages.takeLast(8) else messages
        return recent.joinToString("\n")
    }

    fun fillWeChatInputBox(text: String): Boolean {
        val targetRoot = findWeChatWindowRoot() ?: return false
        val editNode = findEditTextNode(targetRoot) ?: return false

        editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findWeChatWindowRoot(): AccessibilityNodeInfo? {
        val allWindows = windows ?: return rootInActiveWindow
        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root ?: continue
                if (root.packageName?.toString() == "com.tencent.mm") {
                    return root
                }
            }
        }
        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg != packageName && !pkg.contains("systemui")) {
                    return root
                }
            }
        }
        return rootInActiveWindow
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        if ((cls.contains("EditText") || node.isEditable) && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findEditTextNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun traverseTextNodes(
        node: AccessibilityNodeInfo?,
        screenWidth: Int,
        topLimit: Int,
        bottomLimit: Int,
        out: MutableList<String>
    ) {
        if (node == null) return

        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText")) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && node.childCount == 0 && node.isVisibleToUser) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top >= topLimit && rect.bottom <= bottomLimit && rect.width() > 0 && rect.height() > 0) {
                val centerX = rect.centerX()
                val isCentered = centerX > screenWidth * 0.40 && centerX < screenWidth * 0.60 && rect.width() < screenWidth * 0.45
                if (!isCentered) {
                    val speaker = if (rect.left < screenWidth * 0.28) "对方" else "我"
                    out.add("$speaker：$text")
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseTextNodes(node.getChild(i), screenWidth, topLimit, bottomLimit, out)
        }
    }
}
