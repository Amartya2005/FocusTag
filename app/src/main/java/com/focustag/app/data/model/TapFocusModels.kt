package com.focustag.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ServerFocusState {
    @SerialName("FOCUS_ACTIVE") FOCUS_ACTIVE,
    @SerialName("ENDED") ENDED
}

@Serializable
enum class AcsHealth {
    @SerialName("HEALTHY") HEALTHY,
    @SerialName("DEGRADED") DEGRADED,
    @SerialName("FAILED") FAILED,
    @SerialName("UNKNOWN") UNKNOWN
}

@Serializable
data class TapFocusResponse(
    val accepted: Boolean = false,
    @SerialName("session_id") val sessionId: String? = null,
    val state: ServerFocusState? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("policy_version") val policyVersion: String? = null,
    @SerialName("acs_hint") val acsHint: String? = null,
    val error: String? = null,
    val force: Boolean? = null
)
