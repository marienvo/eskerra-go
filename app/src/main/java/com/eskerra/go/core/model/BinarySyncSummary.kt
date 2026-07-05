package com.eskerra.go.core.model

/** Outcome of a binaries sync run against R2. */
sealed interface BinarySyncSummary {
    /** R2 is not configured in vault settings; nothing was attempted. */
    data object NotConfigured : BinarySyncSummary

    /** The sync ran to completion. */
    data class Completed(val downloaded: Int, val deleted: Int, val skipped: Int) :
        BinarySyncSummary

    /** The sync failed part-way; [message] is user-facing. */
    data class Failed(val message: String) : BinarySyncSummary
}
