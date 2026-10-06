package com.focustag.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focustag.app.ui.components.ClassroomTopBar
import com.focustag.app.ui.components.RoleBadge
import com.focustag.app.data.repository.AdminClass
import com.focustag.app.data.repository.AdminEnrollment
import com.focustag.app.data.repository.AdminLocation
import com.focustag.app.data.repository.AdminProfile
import com.focustag.app.data.repository.AdminRepository
import com.focustag.app.data.repository.AdminTag
import kotlinx.coroutines.launch

private val adminTabs = listOf("Classes", "Enrollments", "NFC tags", "Policies")

@Composable
fun AdminScreen(institutionId: String, onBack: () -> Unit) {
    val repository = remember(institutionId) { AdminRepository(institutionId) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var classes by remember { mutableStateOf<List<AdminClass>>(emptyList()) }
    var locations by remember { mutableStateOf<List<AdminLocation>>(emptyList()) }
    var students by remember { mutableStateOf<List<AdminProfile>>(emptyList()) }
    var enrollments by remember { mutableStateOf<List<AdminEnrollment>>(emptyList()) }
    var tags by remember { mutableStateOf<List<AdminTag>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            repository.locations().onSuccess { locations = it }.onFailure { message = it.message }
            repository.classes().onSuccess { classes = it }.onFailure { message = it.message }
            repository.students().onSuccess { students = it }.onFailure { message = it.message }
            repository.tags().onSuccess { tags = it }.onFailure { message = it.message }
            loading = false
        }
    }

    LaunchedEffect(institutionId) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ClassroomTopBar(
            title = "Institution admin",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleBadge("admin")
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        )
        Text(
            institutionId.ifBlank { "no institution" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            adminTabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        when (selectedTab) {
            0 -> ClassesTab(repository, classes, locations, { refresh() }, { message = it })
            1 -> EnrollmentsTab(repository, classes, students, enrollments, { enrollments = it }, { message = it })
            2 -> TagsTab(repository, tags, locations, { refresh() }, { message = it })
            3 -> PoliciesTab(repository, classes, { message = it })
        }
    }
}
