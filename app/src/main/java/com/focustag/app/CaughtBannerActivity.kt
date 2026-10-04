package com.focustag.app

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

class CaughtBannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(TextView(this).apply {
            text = intent.getStringExtra("line") ?: "Back to class"
            setTextColor(0xFFF6F1E8.toInt())
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setPadding(72, 72, 72, 72)
            setBackgroundColor(0xFF1B2428.toInt())
            setOnClickListener { finish() }
        })
    }
}
