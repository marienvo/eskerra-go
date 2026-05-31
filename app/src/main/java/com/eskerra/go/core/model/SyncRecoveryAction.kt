package com.eskerra.go.core.model

/** User-facing recovery hint for a sync failure or partial success. */
data class SyncRecoveryAction(
    val hint: String,
    val suggestOpenSettings: Boolean = false,
    val localNotesAvailable: Boolean = true
)
