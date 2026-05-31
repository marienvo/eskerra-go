package com.eskerra.go.core.model

/** Outcome of a manual sync that completed Git operations (registry may have failed). */
data class SyncResult(
    val status: SyncStatusSummary,
    val committed: Boolean,
    val commitId: String?,
    val pushed: Boolean,
    val pulled: Boolean,
    val registryRefreshed: Boolean = true,
    val updatedConfig: WorkspaceConfig? = null
)
