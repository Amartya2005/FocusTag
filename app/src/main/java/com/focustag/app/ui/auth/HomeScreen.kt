package com.focustag.app.ui.auth

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.BuildConfig
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.NfcCapability
import com.focustag.app.ui.acs.AcsRequiredGate
import com.focustag.app.ui.components.ClassroomTopBar
import com.focustag.app.ui.components.ErrorBanner
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
            body = "Class cannot lock message replies until FocusTag can clear chat notifications. Enable FocusTag under Notification access, then return here.",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        ClassroomTopBar(
            title = if (isFocusActive) "In class" else "At the door",
            trailing = { InitialsAvatar(userEmail, onClick = if (isFocusActive) null else onNavigateToProfile) }
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            onClick = { if (!isTransitioning) onNavigateToQr() },
            enabled = !isTransitioning,
            shape = RoundedCornerShape(32.dp),
            color = cardColor,
            shadowElevation = if (isFocusActive) 0.dp else 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulseDot(active = isFocusActive)
                    Text(
                        if (isFocusActive) "LIVE" else "READY",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFocusActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        enforcementLabel(enforcementStatus),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedContent(
                    targetState = isFocusActive,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "focusCopy"
                ) { live ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (live) "Focus on" else "Focus off",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            if (live) "Tap the same tag or scan to leave."
                            else "Hold the tag, or tap here to scan.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    when (nfcCapability) {
                        NfcCapability.NFC_READY -> "NFC ready"
                        NfcCapability.NFC_OFF -> "NFC off — tap to scan"
                        NfcCapability.NFC_UNAVAILABLE -> "Scan to start"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        lastTapMessage?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(it)
        }
        if (isTransitioning) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("One moment…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (nfcCapability == NfcCapability.NFC_OFF) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Turn on NFC") }
        }
        Spacer(Modifier.weight(1f))
        QuietLinkRow(
            items = listOf(
                "Today" to onBack,
                "History" to onNavigateToHistory,
                "Apps" to onNavigateToApps
            ),
            enabled = !isFocusActive && !isTransitioning
        )
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { focusViewModel.onTagEvent("1D:1D:70:1C:1A:10:80") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTransitioning,
                shape = RoundedCornerShape(16.dp)
            ) { Text("Debug: Pilot Room A") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun enforcementLabel(status: EnforcementStatus): String = when (status) {
    EnforcementStatus.ENFORCEMENT_ACTIVE -> "locked"
    EnforcementStatus.ENFORCEMENT_SIMULATED -> "practice"
    EnforcementStatus.ENFORCEMENT_DEGRADED -> "hold"
    EnforcementStatus.ENFORCEMENT_FAILED -> "failed"
    EnforcementStatus.IDLE -> "idle"
    else -> status.name.lowercase().replace('_', ' ')
}
