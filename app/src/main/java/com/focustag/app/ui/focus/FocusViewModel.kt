package com.focustag.app.ui.focus

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustag.app.BuildConfig
import com.focustag.app.data.model.*
import com.focustag.app.data.repository.FocusRepository
import com.focustag.app.data.repository.NfcRepository
import com.focustag.app.data.repository.NfcRegistryCache
import com.focustag.app.data.supabase.SupabaseModule
import io.github.jan.supabase.auth.auth
import com.focustag.app.domain.EnforcementCoordinator
import com.focustag.app.domain.FocusStateEngine
import com.focustag.app.domain.NfcProtocol
import com.focustag.app.util.AccessibilityCapabilityChecker
import java.util.UUID
import com.focustag.app.util.InstallIdStore
import com.focustag.app.data.remote.TapFocusRepository
import com.focustag.app.data.model.ServerFocusState
import com.focustag.app.data.model.AcsHealth
import com.focustag.app.util.NfcCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class FocusViewModel(
    private val context: Context?,
    private val focusRepository: FocusRepository,
    private val enforcementCoordinator: EnforcementCoordinator,
    private val nfcRepository: NfcRepository? = null,
    private val tapFocusRepository: TapFocusRepository = TapFocusRepository(),
    private val currentUserIdProvider: () -> String? = {
        try { SupabaseModule.client.auth.currentSessionOrNull()?.user?.id } catch (e: Exception) { null }
    }
) : ViewModel() {

    private companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        const val MAX_CACHE_AGE_MS = 48 * 60 * 60 * 1000L
        const val START_DEBOUNCE_MS = 4000L
        const val RELEASE_DEBOUNCE_MS = 500L
    }

    protected val _focusState = MutableStateFlow(focusRepository.getFocusSessionState())
    val focusState = _focusState.asStateFlow()
    protected val _accessibilityCapability = MutableStateFlow(AccessibilityCapability.ACCESSIBILITY_UNAVAILABLE)
    val accessibilityCapability = _accessibilityCapability.asStateFlow()
    protected val _nfcCapability = MutableStateFlow(NfcCapability.NFC_UNAVAILABLE)
    val nfcCapability = _nfcCapability.asStateFlow()
    protected val _isTransitioning = MutableStateFlow(false)
    val isTransitioning = _isTransitioning.asStateFlow()
    protected val _acsBlocked = MutableStateFlow(false)
    val acsBlocked = _acsBlocked.asStateFlow()
    protected val _lastTapMessage = MutableStateFlow<String?>(null)
    val lastTapMessage = _lastTapMessage.asStateFlow()
    val enforcementStatus = enforcementCoordinator.status

    private var lastRefreshTime = 0L
    private var isRefreshing = false
    private var lastAcceptedTagId: String? = null
    private var lastAcceptedAtMs: Long = 0L

    init {
        refreshAccessibilityCapability()
        refreshNfcCapability()
        loadInitialRegistry()
        refreshRegistry()
        viewModelScope.launch {
            if (_focusState.value.focusState == FocusState.FOCUS_ACTIVE) {
                _isTransitioning.update { true }
                try {
                    enforcementCoordinator.reconcile()
                    if (!isEnforcementActive(enforcementCoordinator.status.value)) {
                        val recoveredState = FocusSessionState(FocusState.NORMAL, null)
                        focusRepository.saveFocusSessionState(recoveredState)
                        _focusState.update { recoveredState }
                    }
                } finally {
                    _isTransitioning.update { false }
                }
            } else {
                enforcementCoordinator.checkAndHandleOrphans()
                refreshEnforcementStatus()
            }
        }
        viewModelScope.launch {
            accessibilityCapability.collect { capability ->
                val isReady = capability == AccessibilityCapability.ACCESSIBILITY_READY
                enforcementCoordinator.refreshStatus(isReady)
                if (isReady && _focusState.value.focusState == FocusState.FOCUS_ACTIVE) {
                    enforcementCoordinator.reconcile()
                }
            }
        }
    }

    open fun refreshAccessibilityCapability() {
        _accessibilityCapability.update { AccessibilityCapabilityChecker.checkAccessibilityCapability(context!!) }
        if (_accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY) _acsBlocked.update { false }
    }

    open fun refreshNfcCapability() {
        _nfcCapability.update { NfcCapabilityChecker.checkNfcCapability(context!!) }
    }

    open fun refreshEnforcementStatus() {
        enforcementCoordinator.refreshStatus(_accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY)
    }

    private fun loadInitialRegistry() {
        val nfcRepo = nfcRepository ?: run { NfcProtocol.setRegisteredTags(emptySet()); return }
        val currentUserId = currentUserIdProvider() ?: run { NfcProtocol.setRegisteredTags(emptySet()); return }
        val cache = nfcRepo.getCache() ?: run { NfcProtocol.setRegisteredTags(emptySet()); return }
        val now = System.currentTimeMillis()
        val verifiedInstitutionId = context?.getSharedPreferences("verified_profile_$currentUserId", Context.MODE_PRIVATE)?.getString("institution_id", null)
        if (verifiedInstitutionId == null || verifiedInstitutionId != cache.fetchedForInstitutionId) {
            NfcProtocol.setRegisteredTags(emptySet()); return
        }
        if (now - cache.fetchedAtMillis > MAX_CACHE_AGE_MS) {
            NfcProtocol.setRegisteredTags(emptySet()); return
        }
        NfcProtocol.setRegisteredTags(cache.activeUids)
    }

    fun refreshRegistry(force: Boolean = false) {
        if (isRefreshing || nfcRepository == null) return
        val now = System.currentTimeMillis()
        val shouldBypassThrottle = force || NfcProtocol.isRegistryEmpty()
        if (!shouldBypassThrottle && now - lastRefreshTime < REFRESH_INTERVAL_MS && lastRefreshTime != 0L) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                nfcRepository.fetchProfile().onSuccess { profile ->
                    val institutionId = profile?.institutionId
                    val currentUserId = currentUserIdProvider()
                    if (currentUserId != null) {
                        val prefs = context?.getSharedPreferences("verified_profile_$currentUserId", Context.MODE_PRIVATE)
                        if (institutionId != null) prefs?.edit()?.putString("institution_id", institutionId)?.apply()
                        else prefs?.edit()?.remove("institution_id")?.apply()
                    }
                    if (institutionId == null) {
                        NfcProtocol.setRegisteredTags(emptySet())
                    } else {
                        val cache = nfcRepository.getCache()
                        if (cache != null && cache.fetchedForInstitutionId != institutionId) {
                            NfcProtocol.setRegisteredTags(emptySet())
                            nfcRepository.saveCache(NfcRegistryCache(emptySet(), emptyMap(), institutionId, 0))
                        }
                        nfcRepository.fetchActiveTagsWithNames().onSuccess { tagMap ->
                            val normalizedTagMap = tagMap.mapKeys { (uid, _) -> NfcProtocol.normalize(uid) ?: uid }
                            NfcProtocol.setRegisteredTags(normalizedTagMap.keys)
                            nfcRepository.saveCache(NfcRegistryCache(normalizedTagMap.keys, normalizedTagMap, institutionId, now))
                            lastRefreshTime = now
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusViewModel", "Error refreshing NFC registry: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun onSimulatedTagTap() {
        if (BuildConfig.DEBUG) onTagEvent("simulated_tag_01", EntrySource.NFC)
    }

    fun onTagEvent(tagId: String, source: EntrySource = EntrySource.NFC) {
        if (_isTransitioning.value) return
        refreshAccessibilityCapability()
        val acsReady = _accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY
        if (!acsReady) { _acsBlocked.update { true }; return }
        _acsBlocked.update { false }
        val normalized = NfcProtocol.normalize(tagId) ?: tagId
        if (normalized == "simulated_tag_01") {
            if (!BuildConfig.DEBUG) {
                _lastTapMessage.update { "That tag is not registered for a class." }
                return
            }
            val transition = FocusStateEngine.determineTransition(_focusState.value, tagId)
            if (transition is FocusTransition.Ignore) return
            viewModelScope.launch {
                _isTransitioning.update { true }
                try { executeLocalTransition(transition); stampAcceptedDebounce(normalized) }
                finally { _isTransitioning.update { false } }
            }
            return
        }
        if (!NfcProtocol.isRegistryEmpty() && !NfcProtocol.isRegistered(normalized)) {
            refreshRegistry(force = true)
        }
        val activeSource = _focusState.value.entrySource
        if (_focusState.value.focusState == FocusState.FOCUS_ACTIVE && activeSource != null && activeSource != source) {
            _lastTapMessage.update { humanizeTapError("entry_mismatch:${activeSource.name.lowercase()}") }
            return
        }
        if (!passesAcceptedDebounce(normalized)) return
        viewModelScope.launch {
            _isTransitioning.update { true }
            try { executeServerAuthoritativeTap(normalized, source) }
            catch (e: Exception) { _lastTapMessage.update { e.message ?: "Could not start the session." } }
            finally { _isTransitioning.update { false } }
        }
    }

    private fun passesAcceptedDebounce(tagId: String): Boolean {
        val now = System.currentTimeMillis()
        val window = if (_focusState.value.focusState == FocusState.FOCUS_ACTIVE) RELEASE_DEBOUNCE_MS else START_DEBOUNCE_MS
        return !(tagId == lastAcceptedTagId && (now - lastAcceptedAtMs) < window)
    }

    private fun stampAcceptedDebounce(tagId: String) {
        lastAcceptedTagId = tagId
        lastAcceptedAtMs = System.currentTimeMillis()
    }

    private suspend fun executeServerAuthoritativeTap(normalizedTagId: String, source: EntrySource) {
        val ctx = context ?: return
        val installUuid = InstallIdStore.getOrCreate(ctx)
        val acsHealth = if (_accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY) AcsHealth.HEALTHY else AcsHealth.FAILED
        val result = tapFocusRepository.tapFocus(tagUid = normalizedTagId, installUuid = installUuid, acsHealth = acsHealth, entrySource = source, idempotencyKey = UUID.randomUUID())
        val response = result.getOrNull()
        if (response == null || !response.accepted) {
            _lastTapMessage.update { humanizeTapError(response?.error ?: result.exceptionOrNull()?.message) }
            return
        }
        _lastTapMessage.update { null }
        stampAcceptedDebounce(normalizedTagId)
        when (response.state) {
            ServerFocusState.FOCUS_ACTIVE -> {
                val classBlocks = tapFocusRepository.blockedPackagesForClass(response.classId)
                enforcementCoordinator.startEnforcement(normalizedTagId, classBlocks)
                val newState = FocusSessionState(FocusState.FOCUS_ACTIVE, normalizedTagId, source)
                focusRepository.saveFocusSessionState(newState)
                _focusState.update { newState }
                if (!isEnforcementActive()) {
                    _lastTapMessage.update { "Class is on, but the lock did not arm. Check Accessibility." }
                } else if (classBlocks.isEmpty()) {
                    _lastTapMessage.update { "Class is on. No apps are on the block list yet." }
                }
            }
            ServerFocusState.ENDED -> {
                enforcementCoordinator.stopEnforcement()
                if (enforcementCoordinator.status.value == EnforcementStatus.IDLE) {
                    val newState = FocusSessionState(FocusState.NORMAL, null, null)
                    focusRepository.saveFocusSessionState(newState)
                    _focusState.update { newState }
                }
            }
            null -> _lastTapMessage.update { "Server accepted the tap but sent no session state." }
        }
    }

    private suspend fun executeLocalTransition(transition: FocusTransition) {
        when (transition) {
            is FocusTransition.Start -> {
                enforcementCoordinator.startEnforcement(transition.tagId)
                if (isEnforcementActive()) {
                    val newState = FocusSessionState(FocusState.FOCUS_ACTIVE, transition.tagId)
                    focusRepository.saveFocusSessionState(newState)
                    _focusState.update { newState }
                }
            }
            is FocusTransition.Stop -> {
                enforcementCoordinator.stopEnforcement()
                if (enforcementCoordinator.status.value == EnforcementStatus.IDLE) {
                    val newState = FocusSessionState(FocusState.NORMAL, null)
                    focusRepository.saveFocusSessionState(newState)
                    _focusState.update { newState }
                }
            }
            is FocusTransition.Ignore -> {}
        }
    }

    fun clearAcsBlocked() {
        refreshAccessibilityCapability()
        if (_accessibilityCapability.value == AccessibilityCapability.ACCESSIBILITY_READY) _acsBlocked.update { false }
    }

    private fun humanizeTapError(code: String?): String = when (code) {
        "not_enrolled" -> "This account is not enrolled in the class for that tag."
        "unknown_or_inactive_tag" -> "That tag is not registered for a class."
        "unauthenticated" -> "Sign in again, then tap or scan."
        "forbidden_force_release" -> "Not allowed to force-release that session."
        "entry_mismatch", "entry_mismatch:nfc" -> "This class started with the NFC tag. Tap that tag to leave."
        "entry_mismatch:qr" -> "This class started with the QR. Scan that code to leave."
        null -> "Could not start the session. Try again."
        else -> code
    }

    private fun isEnforcementActive(s: EnforcementStatus = enforcementCoordinator.status.value): Boolean {
        return s == EnforcementStatus.ENFORCEMENT_ACTIVE || s == EnforcementStatus.ENFORCEMENT_SIMULATED || s == EnforcementStatus.ENFORCEMENT_DEGRADED
    }
}
