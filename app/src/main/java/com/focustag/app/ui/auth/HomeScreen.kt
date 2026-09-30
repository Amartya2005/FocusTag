package com.focustag.app.ui.auth

import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.BuildConfig
import com.focustag.app.data.model.AccessibilityCapability
import com.focustag.app.data.model.EnforcementStatus
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.model.NfcCapability
import com.focustag.app.ui.acs.AcsRequiredGate
import com.focustag.app.ui.focus.FocusViewModel
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

    if (acsBlocked || accessibilityCapability != AccessibilityCapability.ACCESSIBILITY_READY) {
        AcsRequiredGate()
        return
    }

    val context = LocalContext.current
    val userEmail = when (val status = sessionStatus) {
        is SessionStatus.Authenticated -> status.session.user?.email ?: "Student"
        else -> "Guest"
    }
    val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE
    val nfcReady = nfcCapability == NfcCapability.NFC_READY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Dashboard") }
            Text("CLASSROOM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isFocusActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("FT", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(if (isFocusActive) "Class in session" else "Ready at the door", style = MaterialTheme.typography.titleLarge)
                        Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    if (isFocusActive) "FOCUS ON" else "FOCUS OFF",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (isFocusActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (isFocusActive) "Apps are restricted until you tap or scan the same classroom tag. Uninstall is locked for this session."
                    else "Tap the NFC tag or scan the door QR to start. Same registered UID either way.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(enforcementLabel(enforcementStatus), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (nfcCapability) {
                        NfcCapability.NFC_READY -> "NFC ready \u00b7 QR always available"
                        NfcCapability.NFC_OFF -> "NFC is off \u00b7 use QR at the door"
                        NfcCapability.NFC_UNAVAILABLE -> "No NFC hardware \u00b7 use QR"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToQr, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !isTransitioning) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (isFocusActive) "Scan QR to leave" else "Scan classroom QR")
        }
        if (nfcCapability == NfcCapability.NFC_OFF) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Nfc, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Turn on NFC")
            }
        }
        if (nfcReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isFocusActive) "Hold the same tag to the phone to release." else "Hold the classroom tag to the phone to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isTransitioning) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Talking to server\u2026", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = { focusViewModel.onTagEvent("1D:1D:70:1C:1A:10:80") }, modifier = Modifier.fillMaxWidth(), enabled = !isTransitioning) {
                Text("Smoke: Pilot Room A QR UID")
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = focusViewModel::onSimulatedTagTap, modifier = Modifier.fillMaxWidth(), enabled = !isTransitioning) {
                Text(if (isFocusActive) "Local simulate release" else "Local simulate start")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onNavigateToApps, modifier = Modifier.fillMaxWidth(), enabled = !isFocusActive) { Text("Allowed apps") }
        if (isFocusActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Settings and uninstall stay locked until the same tag or QR ends class.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToHistory, modifier = Modifier.fillMaxWidth(), enabled = !isFocusActive) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Session history")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToProfile, modifier = Modifier.fillMaxWidth(), enabled = !isFocusActive) {
            Icon(Icons.Outlined.Person, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Profile")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { authViewModel.signOut(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFocusActive,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
        ) { Text("Sign out") }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun enforcementLabel(status: EnforcementStatus): String = when (status) {
    EnforcementStatus.ENFORCEMENT_ACTIVE -> "Policy: enforcing"
    EnforcementStatus.ENFORCEMENT_SIMULATED -> "Policy: simulated"
    EnforcementStatus.ENFORCEMENT_DEGRADED -> "Policy: degraded \u2014 fail-closed"
    EnforcementStatus.ENFORCEMENT_FAILED -> "Policy: failed \u2014 fail-closed"
    EnforcementStatus.IDLE -> "Policy: idle"
    else -> "Policy: ${status.name.lowercase().replace('_', ' ')}"
}
