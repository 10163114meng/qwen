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
            text = "使用说明：\n1. 点击下方按钮开启悬浮窗\n2. 首次使用请在悬浮窗内登录千问账号\n3. 聊天时复制对方的话，点「生成回复」即可"
            textSize = 15f
            setPadding(0, 0, 0, 48)
        }

        val btnStart = Button(this).apply {
            text = "开启微信聊天悬浮窗"
            textSize = 16f
        }

        layout.addView(info)
        layout.addView(btnStart)
        setContentView(layout)

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
