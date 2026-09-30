package com.focustag.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focustag.app.data.model.FocusSessionState
import com.focustag.app.data.model.FocusState
import com.focustag.app.data.repository.SupabaseAuthRepository
import com.focustag.app.data.repository.SupabaseProfileRepository
import com.focustag.app.data.repository.AppInventoryRepository
import com.focustag.app.data.repository.AppPolicyRepository
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.repository.NfcRepository
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.data.repository.SupabaseHistoryRepository
import com.focustag.app.data.repository.SupabaseTeacherRepository
import com.focustag.app.data.supabase.SupabaseModule
import com.focustag.app.data.worker.SyncScheduler
import com.focustag.app.domain.EnforcementCoordinatorHub
import com.focustag.app.ui.admin.AdminScreen
import com.focustag.app.ui.apps.AppSelectionScreen
import com.focustag.app.ui.apps.AppSelectionViewModel
import com.focustag.app.ui.auth.AuthViewModel
import com.focustag.app.ui.auth.HomeScreen
import com.focustag.app.ui.auth.LoginScreen
import com.focustag.app.ui.auth.SignupScreen
import com.focustag.app.ui.focus.FocusViewModel
import com.focustag.app.ui.history.HistoryScreen
import com.focustag.app.ui.history.HistoryViewModel
import com.focustag.app.ui.profile.ProfileScreen
import com.focustag.app.ui.profile.ProfileViewModel
import com.focustag.app.ui.qr.QrScanScreen
import com.focustag.app.ui.teacher.ClassRosterScreen
import com.focustag.app.ui.teacher.TeacherClassesScreen
import com.focustag.app.ui.teacher.TeacherViewModel
import com.focustag.app.ui.theme.FocusTagTheme
import com.focustag.app.util.NfcController
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var nfcController: NfcController
    private var activeFocusViewModel: FocusViewModel? = null
    private var pendingTagUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseModule.initialize(applicationContext)
        nfcController = NfcController(this) {
            activeFocusViewModel?.focusState?.value?.focusState == FocusState.FOCUS_ACTIVE
        }
        SessionHistoryRepository.initCollector(applicationContext)
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        setContent {
            FocusTagTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val authViewModel: AuthViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AuthViewModel(SupabaseAuthRepository()) as T
                            }
                        }
                    )
                    val profileViewModel: ProfileViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ProfileViewModel(SupabaseProfileRepository()) as T
                            }
                        }
                    )
                    val sessionStatus by authViewModel.sessionStatus.collectAsState()
                    val uiState by authViewModel.uiState.collectAsState()
                    val profileState by profileViewModel.uiState.collectAsState()
                    var currentScreen by remember { mutableStateOf("home") }

                    val appSelectionViewModel: AppSelectionViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(key = userId, factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AppSelectionViewModel(AppInventoryRepository(this@MainActivity), AppPolicyRepository(this@MainActivity, userId)) as T
                            }
                        })
                    } else null

                    val focusViewModel: FocusViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(key = "focus_$userId", factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                val coordinator = EnforcementCoordinatorHub.getCoordinator(this@MainActivity, userId)
                                return FocusViewModel(
                                    context = this@MainActivity.applicationContext,
                                    focusRepository = FocusRepository(this@MainActivity, userId),
                                    enforcementCoordinator = coordinator,
                                    nfcRepository = NfcRepository(this@MainActivity.applicationContext, userId)
                                ) as T
                            }
                        })
                    } else null

                    val historyViewModel: HistoryViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(key = "history_$userId", factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return HistoryViewModel(userId = userId, localRepo = SessionHistoryRepository(this@MainActivity, userId), remoteRepo = SupabaseHistoryRepository(), nfcRepository = NfcRepository(this@MainActivity.applicationContext, userId)) as T
                            }
                        })
                    } else null

                    val dashboardViewModel: com.focustag.app.ui.dashboard.DashboardViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(key = "dashboard_$userId", factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return com.focustag.app.ui.dashboard.DashboardViewModel(localRepo = SessionHistoryRepository(this@MainActivity, userId), nfcRepository = NfcRepository(this@MainActivity.applicationContext, userId)) as T
                            }
                        })
                    } else null

                    val teacherViewModel: TeacherViewModel? = if (sessionStatus is SessionStatus.Authenticated) {
                        val userId = (sessionStatus as SessionStatus.Authenticated).session.user?.id ?: ""
                        viewModel(key = "teacher_$userId", factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return TeacherViewModel(SupabaseTeacherRepository()) as T
                            }
                        })
                    } else null

                    activeFocusViewModel = focusViewModel
                    LaunchedEffect(focusViewModel) {
                        val pending = pendingTagUid ?: return@LaunchedEffect
                        if (focusViewModel != null) {
                            pendingTagUid = null
                            focusViewModel.onTagEvent(pending)
                        }
                    }
                    val focusSessionState by focusViewModel?.focusState?.collectAsState(initial = FocusSessionState()) ?: remember { mutableStateOf(FocusSessionState()) }
                    val isFocusActive = focusSessionState.focusState == FocusState.FOCUS_ACTIVE

                    LaunchedEffect(sessionStatus) {
                        if (sessionStatus is SessionStatus.Authenticated) {
                            val session = (sessionStatus as SessionStatus.Authenticated).session
                            val userId = session.user?.id ?: ""
                            val email = session.user?.email ?: ""
                            profileViewModel.loadProfile(userId, email)
                            focusViewModel?.refreshEnforcementStatus()
                            focusViewModel?.refreshAccessibilityCapability()
                            focusViewModel?.refreshNfcCapability()
                            focusViewModel?.refreshRegistry(force = true)
                            SyncScheduler.scheduleSync(this@MainActivity, userId)
                        } else {
                            com.focustag.app.domain.NfcProtocol.setRegisteredTags(emptySet())
                            currentScreen = "home"
                        }
                    }

                    LaunchedEffect(profileState.role) {
                        if (profileState.role == "admin") currentScreen = "dashboard"
                    }

                    DisposableEffect(Unit) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                focusViewModel?.refreshAccessibilityCapability()
                                focusViewModel?.refreshNfcCapability()
                                focusViewModel?.refreshRegistry(force = true)
                                val userId = (sessionStatus as? SessionStatus.Authenticated)?.session?.user?.id
                                userId?.let { SyncScheduler.scheduleSync(this@MainActivity, it) }
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose { lifecycle.removeObserver(observer) }
                    }

                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (sessionStatus) {
                            is SessionStatus.Initializing -> CircularProgressIndicator()
                            is SessionStatus.Authenticated -> {
                                when (currentScreen) {
                                    "dashboard" -> {
                                        dashboardViewModel?.let {
                                            if (profileState.role == "admin") {
                                                AdminScreen(institutionId = profileState.institutionId.orEmpty(), onBack = { currentScreen = "home" })
                                            } else {
                                                com.focustag.app.ui.dashboard.DashboardScreen(
                                                    viewModel = it,
                                                    isTeacher = profileState.role == "teacher",
                                                    onNavigateToHistory = { currentScreen = "history" },
                                                    onNavigateToFocus = { currentScreen = "home" },
                                                    onNavigateToProfile = { currentScreen = "profile" },
                                                    onNavigateToTeacher = { currentScreen = "teacher_classes" }
                                                )
                                            }
                                        }
                                    }
                                    "teacher_classes" -> teacherViewModel?.let {
                                        TeacherClassesScreen(viewModel = it, onClassClick = { cls -> it.selectClass(cls); currentScreen = "teacher_classes"; currentScreen = "teacher_roster" }, onBack = { currentScreen = "home" })
                                    }
                                    "teacher_roster" -> teacherViewModel?.let {
                                        ClassRosterScreen(viewModel = it, onBack = { it.clearSelection(); currentScreen = "teacher_classes" })
                                    }
                                    "profile" -> ProfileScreen(viewModel = profileViewModel, isFocusActive = isFocusActive, onBack = { currentScreen = "home" })
                                    "apps" -> appSelectionViewModel?.let { AppSelectionScreen(viewModel = it, isFocusActive = isFocusActive, onBack = { currentScreen = "home" }) }
                                    "history" -> historyViewModel?.let { HistoryScreen(viewModel = it, onBack = { currentScreen = "home" }) }
                                    "qr_scan" -> focusViewModel?.let { focusVM ->
                                        QrScanScreen(
                                            isFocusActive = isFocusActive,
                                            onUidResolved = { uid -> focusVM.onTagEvent(uid); currentScreen = "home" },
                                            onCancel = { currentScreen = "home" }
                                        )
                                    }
                                    else -> focusViewModel?.let { focusVM ->
                                        HomeScreen(
                                            authViewModel = authViewModel,
                                            focusViewModel = focusVM,
                                            onNavigateToProfile = { currentScreen = "profile" },
                                            onNavigateToApps = { currentScreen = "apps" },
                                            onNavigateToHistory = { currentScreen = "history" },
                                            onNavigateToQr = { currentScreen = "qr_scan" },
                                            onBack = { currentScreen = "dashboard" }
                                        )
                                    }
                                }
                            }
                            else -> if (uiState.isLoginMode) LoginScreen(viewModel = authViewModel) else SignupScreen(viewModel = authViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcController.enableReaderMode { tagId -> deliverTag(tagId) }
    }

    override fun onPause() {
        super.onPause()
        nfcController.disableReaderMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        setIntent(intent)
        SupabaseModule.client.handleDeeplinks(intent)
        val uid = nfcController.uidFromIntent(intent) ?: return
        Log.d("MainActivity", "Tag UID from launch intent")
        deliverTag(uid)
    }

    private fun deliverTag(uid: String) {
        val vm = activeFocusViewModel
        if (vm != null) {
            lifecycleScope.launch(Dispatchers.Main) { vm.onTagEvent(uid) }
        } else {
            pendingTagUid = uid
        }
    }
}
