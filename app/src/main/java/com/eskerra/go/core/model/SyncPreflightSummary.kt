package com.eskerra.go.core.model

/** Local-only snapshot shown before manual sync. */
data class SyncPreflightSummary(
    val canSync: Boolean,
    val blockReason: SyncError?,
    val workspaceReady: Boolean,
    val remoteConfigured: Boolean,
    val credentialPresent: Boolean,
    val inboxChangeCount: Int,
    val nonInboxChangeCount: Int,
    val unsafePathCount: Int,
    val stagedNonInboxCount: Int,
    val stagedUnsafeCount: Int,
    val aheadCount: Int,
    val behindCount: Int,
    val repoInterventionRequired: Boolean,
    val userMessage: String
)
