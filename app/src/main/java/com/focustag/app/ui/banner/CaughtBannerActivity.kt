package com.focustag.app.ui.banner

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CaughtBannerActivity : ComponentActivity() {
    private val closer = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra(EXTRA_APP).orEmpty().ifBlank { "That app" }
        val line = LINES.random().format(appName)
        setContent { CaughtBanner(line) { finish() } }
        closer.postDelayed({ if (!isFinishing) finish() }, 2400)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        closer.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_APP = "app"
        private val LINES = listOf(
            "%s tried to sneak in. The door said no.",
            "Nice try. %s is sitting this class out.",
            "%s is in timeout. You are not.",
            "Plot twist: %s can wait until the bell.",
            "Caught. %s goes back in the bag."
        )
    }
}

@Composable
private fun CaughtBanner(line: String, onDismiss: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "wiggle")
    val scale by motion.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .background(Color(0xFF1B2428), RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NOPE", color = Color(0xFFE7B08A), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(line, color = Color(0xFFF6F1E8), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Tap to hide", color = Color(0xFFD5D0C6), fontSize = 13.sp)
        }
    }
}
