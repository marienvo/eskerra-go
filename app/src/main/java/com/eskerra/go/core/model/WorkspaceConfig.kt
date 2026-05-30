package com.eskerra.go.core.model

/**
 * Persisted non-secret metadata for the single configured workspace.
 * [relativePath] is always relative to app-private [android.content.Context.filesDir].
 */
data class WorkspaceConfig(
    val name: String,
    val relativePath: String,
    val remoteUri: String?,
    val branch: String,
    val setupCompletedAtEpochMs: Long
)
