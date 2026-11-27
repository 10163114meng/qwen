package com.example.qwenhelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val info = TextView(this).apply {
            text = "使用说明（v3.1 触控优化版）：\n" +
                "1. 开启「无障碍读屏权限」\n" +
                "2. 开启「左侧小浮标」\n" +
                "3. 首次使用：长按左侧「回」浮标弹出千问窗口，登录一次账号后点“隐藏窗口”\n" +
                "4. 日常聊天：在微信里【单击】左侧「回」浮标即可自动生成并填入输入框"
            textSize = 15f
            setPadding(0, 0, 0, 40)
        }

        val btnAccessibility = Button(this).apply {
            text = "1. 开启无障碍读屏权限"
            textSize = 16f
        }

        val btnStart = Button(this).apply {
            text = "2. 开启左侧小浮标"
            textSize = 16f
        }

        layout.addView(info)
        layout.addView(btnAccessibility)
        layout.addView(btnStart)
        setContentView(layout)

        btnAccessibility.setOnClickListener {
            Toast.makeText(this, "请找到「千问微信读屏助手」并开启", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startService(Intent(this, FloatingQwenService::class.java))
                moveTaskToBack(true)
            }
        }
    }
}
