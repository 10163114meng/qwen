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

    private val ignoreExact = setOf(
        "返回", "更多", "表情", "更多功能按钮", "切换到按住说话", "切换到键盘",
        "按住 说话", "可按住 转文字", "发送", "语音", "图片", "聊天信息"
    )

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
        val roots = getCandidateRoots()
        val messages = mutableListOf<String>()
        val topLimit = (screenHeight * 0.08).toInt()
        val bottomLimit = (screenHeight * 0.88).toInt()

        for (root in roots) {
            traverseTextNodes(root, screenWidth, topLimit, bottomLimit, messages)
            if (messages.isNotEmpty()) break
        }

        val recent = if (messages.size > 10) messages.takeLast(10) else messages
        return recent.joinToString("\n")
    }

    fun fillWeChatInputBox(text: String): Boolean {
        val roots = getCandidateRoots()
        for (root in roots) {
            val editNode = findEditTextNode(root)
            if (editNode != null) {
                editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return true
                }
            }
        }
        return false
    }

    private fun getCandidateRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        val allWindows = try { windows } catch (e: Exception) { null }
        if (allWindows != null) {
            // 1. 优先微信窗口
            for (w in allWindows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == "com.tencent.mm") {
                    list.add(r)
                }
            }
            // 2. 其次其他非本应用的窗口
            for (w in allWindows) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val r = w.root ?: continue
                    val pkg = r.packageName?.toString() ?: ""
                    if (pkg != packageName && pkg != "com.tencent.mm" && !pkg.contains("systemui")) {
                        list.add(r)
                    }
                }
            }
        }
        // 3. 兜底使用 rootInActiveWindow
        rootInActiveWindow?.let { active ->
            if (active.packageName?.toString() != packageName && list.none { it == active }) {
                list.add(active)
            }
        }
        return list
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        if (cls.contains("EditText") || node.isEditable) {
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

        val rawText = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim()

        if (!rawText.isNullOrEmpty() && !shouldIgnore(rawText)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top >= topLimit && rect.bottom <= bottomLimit && rect.width() > 0 && rect.height() > 0) {
                val centerX = rect.centerX()
                val isCenteredTime = centerX > screenWidth * 0.42 && centerX < screenWidth * 0.58 && rect.width() < screenWidth * 0.42
                if (!isCenteredTime) {
                    val speaker = if (rect.left < screenWidth * 0.30) "对方" else "我"
                    val formatted = "$speaker：$rawText"
                    if (out.isEmpty() || out.last() != formatted) {
                        out.add(formatted)
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseTextNodes(node.getChild(i), screenWidth, topLimit, bottomLimit, out)
        }
    }

    private fun shouldIgnore(text: String): Boolean {
        if (text in ignoreExact) return true
        if (text.endsWith("头像") || text.endsWith("的头像")) return true
        return false
    }
}
