package com.eskerra.go.core.model

/** Sanitized remote sync metadata for display and editing. Never includes a token. */
data class RemoteSyncSettings(
    val remoteUri: String?,
    val branch: String,
    val isConfigured: Boolean,
    val hasStoredCredential: Boolean
)
