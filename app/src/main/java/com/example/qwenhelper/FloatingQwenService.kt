package com.example.qwenhelper

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingQwenService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var badgeView: TextView
    private lateinit var badgeBg: GradientDrawable
    private lateinit var badgeParams: WindowManager.LayoutParams

    private lateinit var webContainer: LinearLayout
    private lateinit var webView: WebView
    private lateinit var webParams: WindowManager.LayoutParams

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWebVisible = false
    private var isGenerating = false
    private var screenWidth = 1080
    private var screenHeight = 2340

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        setupBackgroundWebWindow(layoutType)
        setupLeftBadge(layoutType)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBackgroundWebWindow(layoutType: Int) {
        webContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 16f
            alpha = 0.01f
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#ECECEC"))
            setPadding(12, 8, 12, 8)
        }

        val btnHide = Button(this).apply {
            text = "隐藏窗口（后台运行）"
            textSize = 12f
        }
        val btnExit = Button(this).apply {
            text = "彻底关闭"
            textSize = 12f
        }
        topBar.addView(btnHide)
        topBar.addView(btnExit)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.55).toInt()
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(QwenJsBridge(), "AndroidBridge")
            loadUrl("https://tongyi.aliyun.com/qianwen/")
        }

        webContainer.addView(topBar)
        webContainer.addView(webView)

        webParams = WindowManager.LayoutParams(
            2,
            2,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(webContainer, webParams)

        btnHide.setOnClickListener { hideWebWindow() }
        btnExit.setOnClickListener { stopSelf() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLeftBadge(layoutType: Int) {
        val density = resources.displayMetrics.density
        val badgeW = (46 * density).toInt()
        val badgeH = (46 * density).toInt()
        val dragThreshold = (18 * density) // 适配高分屏，避免轻点被误判为拖动

        badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(0f, 0f, 24 * density, 24 * density, 24 * density, 24 * density, 0f, 0f)
            setColor(Color.parseColor("#CC1A73E8"))
        }

        badgeView = TextView(this).apply {
            text = "回"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = badgeBg
            elevation = 10f
        }

        badgeParams = WindowManager.LayoutParams(
            badgeW,
            badgeH,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (screenHeight * 0.45).toInt()
        }

        windowManager.addView(badgeView, badgeParams)

        var startY = 0
        var touchStartY = 0f
        var touchStartX = 0f
        var downTime = 0L
        var isDragging = false
        var longPressed = false

        val longPressRunnable = Runnable {
            if (!isDragging) {
                longPressed = true
                if (isWebVisible) hideWebWindow() else showWebWindow()
            }
        }

        badgeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = badgeParams.y
                    touchStartY = event.rawY
                    touchStartX = event.rawX
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    longPressed = false
                    mainHandler.postDelayed(longPressRunnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - touchStartY
                    val dx = event.rawX - touchStartX
                    if (abs(dy) > dragThreshold || abs(dx) > dragThreshold) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        badgeParams.y = (startY + dy.toInt()).coerceIn(100, screenHeight - 250)
                        windowManager.updateViewLayout(badgeView, badgeParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && !longPressed && elapsed < 600) {
                        onBadgeClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun flashBadgeStatus(label: String, colorHex: String, durationMs: Long = 1800) {
        badgeView.text = label
        badgeBg.setColor(Color.parseColor(colorHex))
        mainHandler.postDelayed({
            if (!isGenerating) {
                badgeView.text = "回"
                badgeBg.setColor(Color.parseColor("#CC1A73E8"))
            }
        }, durationMs)
    }

    private fun showWebWindow() {
        isWebVisible = true
        webContainer.alpha = 1.0f
        webParams.width = (screenWidth * 0.90).toInt()
        webParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        webParams.x = (screenWidth * 0.05).toInt()
        webParams.y = (screenHeight * 0.15).toInt()
        webParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowManager.updateViewLayout(webContainer, webParams)
    }

    private fun hideWebWindow() {
        isWebVisible = false
        webContainer.alpha = 0.01f
        webParams.width = 2
        webParams.height = 2
        webParams.x = 0
        webParams.y = 0
        webParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(webContainer, webParams)
    }

    private fun onBadgeClicked() {
        if (isGenerating) {
            Toast.makeText(this, "正在生成中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        val reader = ScreenReaderService.instance
        if (reader == null) {
            flashBadgeStatus("无权", "#CCE53935")
            Toast.makeText(this, "无障碍服务未连接，请重新开关「千问微信读屏助手」", Toast.LENGTH_LONG).show()
            return
        }

        val chatText = reader.readCurrentScreenChat(screenWidth, screenHeight)
        if (chatText.isBlank()) {
            flashBadgeStatus("空", "#CCFB8C00")
            Toast.makeText(this, "未读到聊天文字，请确认当前在微信聊天页", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        badgeView.text = "..."
        badgeBg.setColor(Color.parseColor("#CC43A047"))

        val marker = "【请直接输出1条回复】"
        val prompt = "以下是微信聊天记录的最后几句：\n$chatText\n$marker 请直接给出1条最自然、得体、像真人聊天的微信回复内容，不要加引号，不要任何前缀解释。"
        sendPromptAndWatchReply(escapeForJs(prompt), marker)
    }

    private fun sendPromptAndWatchReply(escapedPrompt: String, marker: String) {
        val jsCode = """
            (function() {
                if (window._qwenPollTimer) clearInterval(window._qwenPollTimer);

                var textarea = document.querySelector('textarea');
                if (!textarea) {
                    AndroidBridge.onNeedLogin("未找到千问输入框，已自动打开千问窗口，请先登录");
                    return;
                }

                function getReplyBlocks() {
                    var selectors = [
                        '[class*="markdown"]',
                        '[class*="answerItem"]',
                        '[class*="message-content"]',
                        '.tongyi-markdown'
                    ];
                    for (var i = 0; i < selectors.length; i++) {
                        var list = document.querySelectorAll(selectors[i]);
                        if (list && list.length > 0) return list;
                    }
                    return [];
                }

                var beforeCount = getReplyBlocks().length;

                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set;
                nativeSetter.call(textarea, "$escapedPrompt");
                textarea.dispatchEvent(new Event('input', { bubbles: true }));

                setTimeout(function() {
                    var enterEvent = new KeyboardEvent('keydown', {
                        key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true
                    });
                    textarea.dispatchEvent(enterEvent);

                    var sendBtn = document.querySelector('button[type="submit"], [class*="send"], [class*="operateBtn"]');
                    if (sendBtn && !sendBtn.disabled) {
                        try { sendBtn.click(); } catch(e) {}
                    }
                }, 300);

                var lastText = "";
                var stableTicks = 0;
                var totalTicks = 0;

                window._qwenPollTimer = setInterval(function() {
                    totalTicks++;
                    var currentReply = "";

                    var blocks = getReplyBlocks();
                    if (blocks.length > beforeCount) {
                        currentReply = (blocks[blocks.length - 1].innerText || "").trim();
                    } else {
                        var full = document.body.innerText || "";
                        var idx = full.lastIndexOf("$marker");
                        if (idx !== -1) {
                            var after = full.substring(idx + "$marker".length);
                            var cut = after.indexOf("不要任何前缀解释。");
                            if (cut !== -1) {
                                after = after.substring(cut + "不要任何前缀解释。".length);
                            }
                            currentReply = after.trim();
                        }
                    }

                    currentReply = currentReply
                        .replace(/重新生成|复制|赞|踩|分享|深度思考|联网搜索|内容由AI生成.*/g, "")
                        .trim();

                    if (currentReply.length > 0 && currentReply === lastText) {
                        stableTicks++;
                        if (stableTicks >= 2) {
                            clearInterval(window._qwenPollTimer);
                            AndroidBridge.onReplyReady(currentReply);
                            return;
                        }
                    } else {
                        lastText = currentReply;
                        stableTicks = 0;
                    }

                    if (totalTicks >= 25) {
                        clearInterval(window._qwenPollTimer);
                        if (lastText.length > 0) {
                            AndroidBridge.onReplyReady(lastText);
                        } else {
                            AndroidBridge.onNeedLogin("生成超时，请检查千问是否已登录或弹出验证");
                        }
                    }
                }, 1000);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    inner class QwenJsBridge {
        @JavascriptInterface
        fun onReplyReady(reply: String) {
            mainHandler.post {
                isGenerating = false
                val cleanReply = reply.trim().removeSurrounding("“", "”").removeSurrounding("\"")
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("qwen_reply", cleanReply))

                val filled = ScreenReaderService.instance?.fillWeChatInputBox(cleanReply) == true
                if (filled) {
                    flashBadgeStatus("✓", "#CC43A047", 1200)
                    Toast.makeText(this@FloatingQwenService, "已填入输入框，可修改后发送", Toast.LENGTH_SHORT).show()
                } else {
                    flashBadgeStatus("已复制", "#CCFB8C00", 1500)
                    Toast.makeText(this@FloatingQwenService, "未找到输入框，回复已复制到剪贴板", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun onNeedLogin(msg: String) {
            mainHandler.post {
                isGenerating = false
                flashBadgeStatus("登录", "#CCE53935", 1500)
                showWebWindow()
                Toast.makeText(this@FloatingQwenService, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun escapeForJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::badgeView.isInitialized) windowManager.removeView(badgeView)
        if (::webContainer.isInitialized) windowManager.removeView(webContainer)
    }
}
