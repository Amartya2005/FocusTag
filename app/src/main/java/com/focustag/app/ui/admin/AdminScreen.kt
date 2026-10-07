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

@Composable
private fun ClassesTab(repository: AdminRepository, classes: List<AdminClass>, locations: List<AdminLocation>, refresh: () -> Unit, report: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf("") }
    var seeding by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Create a class", style = MaterialTheme.typography.titleMedium) }
        item { Text("Institution scope is taken from the signed-in admin profile.", style = MaterialTheme.typography.bodySmall) }
        item { AdminField("Class name", name) { name = it } }
        item { AdminField("Location ID", locationId) { locationId = it } }
        item { Button(onClick = { scope.launch { repository.createClass(name.trim(), locationId.trim()).fold({ name = ""; locationId = ""; refresh() }, { report(it.message ?: "Could not create class") }) } }, enabled = name.isNotBlank() && locationId.isNotBlank()) { Text("Create class") } }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Demo classroom setup", style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                "Creates or reuses CSB 1 through CSB 10 in this institution and assigns the first available teacher profiles one-to-one to those rooms.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        seeding = true
                        repository.seedDemoCsbClassrooms().fold(
                            {
                                seeding = false
                                report("CSB demo ready: ${it.createdClasses} classes created, ${it.assignedTeachers} teachers assigned.")
                                refresh()
                            },
                            {
                                seeding = false
                                report(it.message ?: "Could not seed CSB demo")
                            }
                        )
                    }
                },
                enabled = !seeding
            ) {
                Text(if (seeding) "Setting up..." else "Seed CSB 1–10 demo")
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Locations", style = MaterialTheme.typography.titleSmall) }
        items(locations) { Text("${it.name} · ${it.id}", style = MaterialTheme.typography.bodySmall) }
        item { Text("Classes", style = MaterialTheme.typography.titleSmall) }
        items(classes) { Text("${it.name} · location ${it.locationId}") }
    }
}

@Composable
private fun EnrollmentsTab(repository: AdminRepository, classes: List<AdminClass>, students: List<AdminProfile>, enrollments: List<AdminEnrollment>, setEnrollments: (List<AdminEnrollment>) -> Unit, report: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var classId by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Add enrollment", style = MaterialTheme.typography.titleMedium) }
        item { AdminField("Class ID", classId) { classId = it } }
        item { AdminField("Student profile ID", studentId) { studentId = it } }
        item { Button(onClick = { scope.launch { repository.enroll(studentId.trim(), classId.trim()).fold({ repository.enrollments(classId.trim()).onSuccess(setEnrollments) }, { report(it.message ?: "Could not enroll student") }) } }, enabled = classId.isNotBlank() && studentId.isNotBlank()) { Text("Enroll student") } }
        item { Text("Known students", style = MaterialTheme.typography.titleSmall) }
        items(students) { Text("${it.name ?: "Unnamed"} · ${it.id}", style = MaterialTheme.typography.bodySmall) }
        item { Text("Enrollments for class", style = MaterialTheme.typography.titleSmall) }
        items(enrollments) { Text("${it.profiles?.name ?: it.studentId} · ${it.studentId}") }
        item { Text("Classes: ${classes.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TagsTab(repository: AdminRepository, tags: List<AdminTag>, locations: List<AdminLocation>, refresh: () -> Unit, report: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var uid by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf("") }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Register NFC / QR tag", style = MaterialTheme.typography.titleMedium) }
        item { AdminField("Tag UID", uid) { uid = it } }
        item { AdminField("Location ID", locationId) { locationId = it } }
        item { Button(onClick = { scope.launch { repository.registerTag(uid, locationId).fold({ uid = ""; refresh() }, { report(it.message ?: "Could not register tag") }) } }, enabled = uid.isNotBlank() && locationId.isNotBlank()) { Text("Register tag") } }
        item { Text("Locations: ${locations.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall) }
        item { Text("Registered tags", style = MaterialTheme.typography.titleSmall) }
        items(tags) { Text("${it.uid} · ${it.locationId} · ${if (it.isActive) "active" else "inactive"}") }
    }
}

@Composable
private fun PoliciesTab(repository: AdminRepository, classes: List<AdminClass>, report: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var classId by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("BLOCK") }
    var packages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var version by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Class policy editor", style = MaterialTheme.typography.titleMedium) }
        item { Text("Save creates an immutable version; students read the latest through get_class_policy.", style = MaterialTheme.typography.bodySmall) }
        item { AdminField("Class ID", classId) { classId = it } }
        item { AdminField("Package name", packageName) { packageName = it } }
        item { AdminField("Action: BLOCK / ALLOW / PROTECTED", action) { action = it.uppercase() } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { if (packageName.isNotBlank()) { packages = packages + (packageName.trim() to action); packageName = "" } }) { Text("Add package") }; Button(onClick = { scope.launch { repository.savePolicy(classId.trim(), packages).fold({ packages = emptyList(); version = "saved" }, { report(it.message ?: "Could not save policy") }) } }, enabled = classId.isNotBlank() && packages.isNotEmpty()) { Text("Save policy") } } }
        items(packages) { (name, value) -> Text("$name → $value") }
        item { OutlinedButton(onClick = { scope.launch { repository.latestPolicy(classId.trim()).fold({ version = it?.version ?: "none" }, { report(it.message ?: "Could not load policy") }) } }, enabled = classId.isNotBlank()) { Text("Load latest") } }
        item { Text("Latest version: ${version ?: "not loaded"}", style = MaterialTheme.typography.bodySmall) }
        item { Text("Classes: ${classes.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun AdminField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
