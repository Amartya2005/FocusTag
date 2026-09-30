package com.focustag.app.ui.auth

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.focustag.app.ui.components.SecondaryRail
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        ClassroomTopBar(
            title = if (isFocusActive) "In class" else "At the door",
            trailing = { InitialsAvatar(userEmail, onClick = if (isFocusActive) null else onNavigateToProfile) }
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isFocusActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isFocusActive) "SESSION LIVE" else "READY",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFocusActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("\u00b7", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        enforcementLabel(enforcementStatus),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (isFocusActive) "Focus on" else "Focus off",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    if (isFocusActive)
                        "Apps, uninstall, and notification replies stay locked until you scan or tap the same classroom tag."
                    else
                        "Scan the door QR or hold the NFC tag. Same registered UID either way.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    when (nfcCapability) {
                        NfcCapability.NFC_READY -> "NFC ready  \u00b7  QR always available"
                        NfcCapability.NFC_OFF -> "NFC is off  \u00b7  use QR"
                        NfcCapability.NFC_UNAVAILABLE -> "No NFC  \u00b7  use QR"
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
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNavigateToQr,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isTransitioning,
            colors = if (isFocusActive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                if (isFocusActive) "Scan to leave class" else "Scan classroom QR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (nfcCapability == NfcCapability.NFC_OFF) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Nfc, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Turn on NFC")
            }
        }
        if (isTransitioning) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Talking to classroom server\u2026", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
        SecondaryRail(
            items = listOf(
                "Today" to onBack,
                "History" to onNavigateToHistory,
                "Apps" to onNavigateToApps
            ),
            enabled = !isFocusActive && !isTransitioning
        )
        if (isFocusActive) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Tools stay closed while class is live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { focusViewModel.onTagEvent("1D:1D:70:1C:1A:10:80") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTransitioning,
                shape = RoundedCornerShape(16.dp)
            ) { Text("Debug: Pilot Room A UID") }
        }
    }
}

private fun enforcementLabel(status: EnforcementStatus): String = when (status) {
    EnforcementStatus.ENFORCEMENT_ACTIVE -> "enforcing"
    EnforcementStatus.ENFORCEMENT_SIMULATED -> "simulated"
    EnforcementStatus.ENFORCEMENT_DEGRADED -> "fail-closed"
    EnforcementStatus.ENFORCEMENT_FAILED -> "failed"
    EnforcementStatus.IDLE -> "idle"
    else -> status.name.lowercase().replace('_', ' ')
}
