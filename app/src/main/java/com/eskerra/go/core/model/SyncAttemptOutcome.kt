package com.eskerra.go.core.model

/** Outcome of the most recent manual sync attempt (non-secret, persisted). */
enum class SyncAttemptOutcome {
    Success,
    PartialSuccess,
    Failed
}
