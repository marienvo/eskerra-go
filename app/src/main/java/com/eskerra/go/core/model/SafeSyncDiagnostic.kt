package com.eskerra.go.core.model

/** Non-secret diagnostics for the sync screen. */
data class SafeSyncDiagnostic(
    val branch: String?,
    val sanitizedRemote: String?,
    val inboxChangeCount: Int,
    val nonInboxChangeCount: Int,
    val aheadCount: Int,
    val behindCount: Int,
    val lastSync: LastSyncStatus?
)
