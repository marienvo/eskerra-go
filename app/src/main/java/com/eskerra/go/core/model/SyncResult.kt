package com.eskerra.go.core.model

/** Outcome of a successful manual sync. */
data class SyncResult(
    val status: SyncStatusSummary,
    val committed: Boolean,
    val commitId: String?,
    val pushed: Boolean,
    val pulled: Boolean,
    val updatedConfig: WorkspaceConfig? = null
)
