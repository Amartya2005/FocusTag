package com.focustag.app.data.model

enum class FocusState {
    NORMAL,
    FOCUS_ACTIVE
}

enum class EntrySource {
    NFC,
    QR
}

data class FocusSessionState(
    val focusState: FocusState = FocusState.NORMAL,
    val activeTagId: String? = null,
    val entrySource: EntrySource? = null
)

/**
 * Represents the logical outcomes of the NFC Protocol v1.
 */
sealed class FocusTransition {
    /** Ignore the tag event. */
    object Ignore : FocusTransition()
    
    /** Start a new focus session. */
    data class Start(val tagId: String) : FocusTransition()
    
    /** Stop the current focus session. */
    object Stop : FocusTransition()
}
