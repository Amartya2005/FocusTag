package com.focustag.app.ui.auth

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.focustag.app.data.model.EntrySource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.NfcCapability
import com.focustag.app.ui.acs.AcsRequiredGate
import com.focustag.app.ui.components.ClassroomTopBar
import com.focustag.app.ui.components.ErrorBanner
import com.focustag.app.ui.components.FunkyStage
import com.focustag.app.ui.components.InitialsAvatar
import com.focustag.app.ui.components.PulseDot
import com.focustag.app.ui.components.QuietLinkRow
import com.focustag.app.ui.focus.FocusViewModel
import com.focustag.app.util.NotificationAccessChecker
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    focusViewModel: FocusViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToQr: () -> Unit,
    onBack: () -> Unit
) {
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val focusSessionState by focusViewModel.focusState.collectAsState()
    val enforcementStatus by focusViewModel.enforcementStatus.collectAsState()
    val isTransitioning by focusViewModel.isTransitioning.collectAsState()
    val accessibilityCapability by focusViewModel.accessibilityCapability.collectAsState()
    val nfcCapability by focusViewModel.nfcCapability.collectAsState()
    val acsBlocked by focusViewModel.acsBlocked.collectAsState()
    val lastTapMessage by focusViewModel.lastTapMessage.collectAsState()
    val context = LocalContext.current

    if (acsBlocked || accessibilityCapability != AccessibilityCapability.ACCESSIBILITY_READY) {
        AcsRequiredGate()
        return
    }
    if (!NotificationAccessChecker.isEnabled(context)) {
        AcsRequiredGate(
            title = "Notification access is required",
            body = "One switch. Then come back to the door.",
            buttonLabel = "Open notification access",
            settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        )
        return
    }

    val userEmail = when (val status = sessionStatus) {
        is SessionStatus.Authenticated -> status.session.user?.email ?: "Student"
        else -> "Guest"
    }
    val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE
    val source = focusSessionState.entrySource
    val wash by animateColorAsState(
        if (isFocusActive) Color(0x33C23B3B) else Color.Transparent,
        tween(500),
        label = "wash"
    )
    val noteMotion = rememberInfiniteTransition(label = "note")
    val noteShift by noteMotion.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shift"
    )

    FunkyStage {
        Box(Modifier.fillMaxSize().background(wash)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClassroomTopBar(
                title = if (isFocusActive) "In class" else "At the door",
                trailing = { InitialsAvatar(userEmail, onClick = if (isFocusActive) null else onNavigateToProfile) }
            )
            val nfcLive = isFocusActive && source == EntrySource.NFC
            val qrLive = isFocusActive && source == EntrySource.QR
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isFocusActive) Color(0xFF3A2424) else Color(0xFF16343A),
                modifier = Modifier.graphicsLayer { translationX = if (isFocusActive) noteShift else 0f }
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulseDot(active = isFocusActive)
                    Text(
                        when {
                            nfcLive -> "Live · tag"
                            qrLive -> "Live · code"
                            else -> "Ready"
                        },
                        color = Color(0xFFF6F1E8),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Surface(
                onClick = onNavigateToQr,
                shape = RoundedCornerShape(28.dp),
                color = if (qrLive) Color(0xFF4A2C28) else Color(0xFF16343A),
                modifier = Modifier.fillMaxWidth().weight(1.35f)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("QR", color = Color(0xFF8FE3DC), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            qrLive -> "Scan the code to leave"
                            nfcLive -> "Tag class is on"
                            else -> "Scan the code to start"
                        },
                        color = Color(0xFFF6F1E8),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (nfcLive) Color(0xFF4A2C28) else Color(0xFF1B2428),
                modifier = Modifier.fillMaxWidth().weight(0.85f)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("NFC", color = Color(0xFFE7B08A), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            nfcLive -> "Tap the tag to leave"
                            qrLive -> "Code class is on"
                            else -> "Hold the tag to start"
                        },
                        color = Color(0xFFF6F1E8),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            lastTapMessage?.let { ErrorBanner(it) }
            if (isTransitioning) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Checking the door", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (nfcCapability == NfcCapability.NFC_OFF) {
                FilledTonalButton(
                    onClick = { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Turn on NFC") }
            }
            QuietLinkRow(
                items = listOf(
                    "Today" to onBack,
                    "History" to onNavigateToHistory,
                    "Apps" to onNavigateToApps
                ),
                enabled = !isFocusActive
            )
        }
        }
    }
}

@Composable
private fun DoorHalo(active: Boolean) {
    val motion = rememberInfiniteTransition(label = "halo")
    val swell by motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(if (active) 1400 else 2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "swell"
    )
    val ring = if (active) Color(0xFFE7B08A) else Color(0xFF8FE3DC)
    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(swell)
            .border(1.5.dp, ring.copy(alpha = 0.35f), CircleShape)
    )
    Box(
        modifier = Modifier
            .size(148.dp)
            .scale(2f - swell)
            .border(2.dp, ring.copy(alpha = 0.7f), CircleShape)
    )
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(ring.copy(alpha = 0.9f), CircleShape)
    )
}

private fun enforcementLabel(status: EnforcementStatus): String = when (status) {
    EnforcementStatus.ENFORCEMENT_ACTIVE -> "locked"
    EnforcementStatus.ENFORCEMENT_SIMULATED -> "practice"
    EnforcementStatus.ENFORCEMENT_DEGRADED -> "hold"
    EnforcementStatus.ENFORCEMENT_FAILED -> "failed"
    EnforcementStatus.IDLE -> "idle"
    else -> status.name.lowercase().replace('_', ' ')
}
