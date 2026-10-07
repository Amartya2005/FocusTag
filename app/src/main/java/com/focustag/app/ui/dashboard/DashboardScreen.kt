package com.focustag.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.ui.components.ClassroomTopBar
import com.focustag.app.ui.components.FunkyStage
import com.focustag.app.ui.components.RoleTopTrailing
import com.focustag.app.ui.history.HistorySessionItem
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    role: String = "student",
    onNavigateToHistory: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToTeacher: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val live = state.activeSession != null
    val isTeacher = role == "teacher" || role == "admin"
    val isAdmin = role == "admin"

    FunkyStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            ClassroomTopBar(
                title = when (role) {
                    "admin" -> "Admin today"
                    "teacher" -> "Teacher today"
                    else -> "Today"
                },
                trailing = {
                    RoleTopTrailing(
                        role = role,
                        avatarLabel = "me",
                        onAvatarClick = onNavigateToProfile
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onNavigateToFocus,
                shape = RoundedCornerShape(32.dp),
                color = if (live) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                shadowElevation = if (live) 0.dp else 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (live) "IN CLASS" else "TODAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (live) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDuration(state.todayDurationMillis),
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        if (live) "Tap to go back to the door."
                        else if (state.todaySessionCount == 0) "No sessions yet — open the door to start with QR or NFC."
                        else "${state.todaySessionCount} sessions  ·  ${state.todayBlockedCount} blocks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextLink("Door", onNavigateToFocus)
                TextLink("History", onNavigateToHistory)
                TextLink("Analytics", onNavigateToAnalytics)
                if (isTeacher) TextLink("Roster", onNavigateToTeacher)
                if (isAdmin) TextLink("Admin", onNavigateToAdmin)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            val recent = state.recentSessions.take(3)
            if (recent.isEmpty()) {
                Surface(
                    onClick = onNavigateToFocus,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "No classes yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Scan a classroom QR or hold an NFC tag at the door to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Open door →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                recent.forEach { RecentRow(it) }
            }
        }
    }
}

@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentRow(item: HistorySessionItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            item.record.status.name.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            formatDuration(item.durationMillis ?: 0L),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(millis: Long): String {
    if (!(millis >= 1L)) return "0m"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
