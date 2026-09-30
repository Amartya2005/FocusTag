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
import androidx.compose.ui.draw.scale
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
    val cardColor by animateColorAsState(
        targetValue = if (isFocusActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        label = "doorColor"
    )
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")

    FunkyStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ClassroomTopBar(
                title = if (isFocusActive) "In class" else "At the door",
                trailing = { InitialsAvatar(userEmail, onClick = if (isFocusActive) null else onNavigateToProfile) }
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { if (!isTransitioning) onNavigateToQr() },
                enabled = !isTransitioning,
                interactionSource = press,
                shape = RoundedCornerShape(36.dp),
                color = cardColor,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scale(pressScale)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DoorHalo(active = isFocusActive)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulseDot(active = isFocusActive)
                            Text(
                                if (isFocusActive) "LIVE" else "READY",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isFocusActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnimatedContent(
                            targetState = isFocusActive,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "focusCopy"
                        ) { live ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (live) "Focus on" else "Focus off",
                                    style = MaterialTheme.typography.displaySmall,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    if (live) "Hold the tag or tap anywhere to leave."
                                    else "Hold the tag or tap anywhere to scan.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Text(
                            when (nfcCapability) {
                                NfcCapability.NFC_READY -> "NFC ready"
                                NfcCapability.NFC_OFF -> "NFC off"
                                NfcCapability.NFC_UNAVAILABLE -> "Scan"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (enforcementStatus != EnforcementStatus.IDLE) {
                            Text(
                                enforcementLabel(enforcementStatus),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            lastTapMessage?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it)
            }
            if (isTransitioning) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("One moment…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (nfcCapability == NfcCapability.NFC_OFF) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Turn on NFC") }
            }
            Spacer(Modifier.height(8.dp))
            QuietLinkRow(
                items = listOf(
                    "Today" to onBack,
                    "History" to onNavigateToHistory,
                    "Apps" to onNavigateToApps
                ),
                enabled = !isFocusActive && !isTransitioning
            )
        }
    }
}

@Composable
private fun DoorHalo(active: Boolean) {
    val motion = rememberInfiniteTransition(label = "halo")
    val swell by motion.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(if (active) 1600 else 2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "swell"
    )
    val color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(240.dp)
            .scale(swell)
            .border(1.5.dp, color.copy(alpha = 0.22f), CircleShape)
    )
    Box(
        modifier = Modifier
            .size(170.dp)
            .scale(2f - swell)
            .border(1.dp, color.copy(alpha = 0.16f), CircleShape)
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
