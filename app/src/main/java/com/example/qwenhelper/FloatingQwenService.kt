package com.example.qwenhelper

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class FloatingQwenService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: LinearLayout
    private lateinit var webView: WebView
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 12f
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setPadding(8, 4, 8, 4)
        }

        val btnGenerate = Button(this).apply {
            text = "生成回复"
            textSize = 12f
        }
        val btnToggle = Button(this).apply {
            text = "折叠"
            textSize = 12f
        }
        val btnClose = Button(this).apply {
            text = "关闭"
            textSize = 12f
        }

        topBar.addView(btnGenerate)
        topBar.addView(btnToggle)
        topBar.addView(btnClose)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.45).toInt()
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            loadUrl("https://tongyi.aliyun.com/qianwen/")
        }

        rootLayout.addView(topBar)
        rootLayout.addView(webView)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            (screenWidth * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth * 0.06).toInt()
            y = 180
        }

        windowManager.addView(rootLayout, params)

        // 支持拖动顶部栏移动悬浮窗
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        topBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootLayout, params)
                    true
                }
                else -> false
            }
        }

        btnToggle.setOnClickListener {
            if (webView.visibility == View.VISIBLE) {
                webView.visibility = View.GONE
                btnToggle.text = "展开"
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                webView.visibility = View.VISIBLE
                btnToggle.text = "折叠"
                params.width = (screenWidth * 0.88).toInt()
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            windowManager.updateViewLayout(rootLayout, params)
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnGenerate.setOnClickListener {
            if (webView.visibility != View.VISIBLE) {
                webView.visibility = View.VISIBLE
                btnToggle.text = "折叠"
                params.width = (screenWidth * 0.88).toInt()
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                windowManager.updateViewLayout(rootLayout, params)
            }

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

            if (clipText.isNullOrBlank()) {
                Toast.makeText(this, "剪贴板为空，请先复制对方的话", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prompt = "对方微信发来这句话：「$clipText」。请帮我生成3条不同风格（委婉高情商、简短直接、幽默）的回复，直接给出回复内容，不要多余解释。"
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

            injectPromptToQwen(prompt)
        }
    }

    private fun injectPromptToQwen(prompt: String) {
        val jsCode = """
            (function() {
                var textarea = document.querySelector('textarea');
                if (!textarea) return;
                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set;
                nativeSetter.call(textarea, "$prompt");
                textarea.dispatchEvent(new Event('input', { bubbles: true }));
                setTimeout(function() {
                    var enterEvent = new KeyboardEvent('keydown', {
                        key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true
                    });
                    textarea.dispatchEvent(enterEvent);
                }, 300);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rootLayout.isInitialized) {
            windowManager.removeView(rootLayout)
        }
    }
}
