package com.eskerra.go.core.model

/**
 * Latest manual sync attempt metadata. Non-secret only; no token, URLs with
 * userinfo, raw exceptions, or filesystem paths.
 */
data class LastSyncStatus(
    val attemptedAtEpochMs: Long,
    val outcome: SyncAttemptOutcome,
    val errorCategory: String?
)
