package com.focustag.app.ui.teacher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.data.model.DashboardStudent
import kotlinx.coroutines.delay

private const val AUTO_REFRESH_MS = 30_000L

/** Teacher home: only students in classes this teacher is assigned to (server-scoped). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    viewModel: TeacherViewModel,
    trailing: @Composable () -> Unit,
    onOpenClasses: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        while (true) { viewModel.loadDashboard(); delay(AUTO_REFRESH_MS) }
    }
    val all = ui.dashboard.flatMap { it.students }.distinctBy { it.id }
    val active = all.count { it.isActive }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Teacher dashboard", fontWeight = FontWeight.Bold) },
            actions = { trailing() }
        )
    }) { pad ->
        PullToRefreshBox(
            isRefreshing = ui.isRefreshing && ui.dashboardLoaded,
            onRefresh = { viewModel.loadDashboard() },
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            if (!ui.dashboardLoaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CountCard("Active", active, true, Modifier.weight(1f))
                        CountCard("Inactive", all.size - active, false, Modifier.weight(1f))
                    }
                }
                ui.dashboardError?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                if (ui.dashboard.isEmpty() && ui.dashboardError == null) item {
                    Text("You aren't assigned to any classes yet.", color = MaterialTheme.colorScheme.outline)
                }
                ui.dashboard.forEach { g ->
                    item(key = "h_" + g.classId) {
                        Text(
                            "${g.className}  ·  ${g.students.count { it.isActive }}/${g.students.size} active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    if (g.students.isEmpty()) item(key = "e_" + g.classId) {
                        Text("No students enrolled", color = MaterialTheme.colorScheme.outline)
                    }
                    items(g.students, key = { g.classId + "_" + it.id }) { StudentRow(it) }
                }
                item { TextButton(onClick = onOpenClasses) { Text("Class rosters") } }
            }
        }
    }
}

@Composable
private fun CountCard(label: String, n: Int, positive: Boolean, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    )) {
        Column(Modifier.padding(16.dp)) {
            Text(n.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StudentRow(s: DashboardStudent) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            AssistChip(
                onClick = {},
                label = { Text(if (s.isActive) "Active" else "Inactive") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (s.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (s.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
