package com.focustag.app.ui.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.FocusAction
import com.focustag.app.data.model.ResolvedPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(viewModel: AppSelectionViewModel, isFocusActive: Boolean, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Class apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Text(
                "Set by the class. No manual on / off here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (state.isLoading && state.resolvedPolicies.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    val grouped = state.resolvedPolicies.groupBy { it.appInfo.category }
                    AppCategory.entries.forEach { category ->
                        val appsInCategory = grouped[category] ?: emptyList()
                        if (appsInCategory.isNotEmpty()) {
                            item {
                                Text(
                                    category.name,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(appsInCategory) { policy ->
                                AppRow(policy)
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(policy: ResolvedPolicy) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(policy.appInfo.appName, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            when (policy.action) {
                FocusAction.PROTECTED -> "keep"
                FocusAction.BLOCK -> "blocked"
                FocusAction.ALLOW -> "ok"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (policy.action) {
                FocusAction.BLOCK -> MaterialTheme.colorScheme.error
                FocusAction.PROTECTED -> MaterialTheme.colorScheme.primary
                FocusAction.ALLOW -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
