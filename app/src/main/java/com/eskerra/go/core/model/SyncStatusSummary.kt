package com.eskerra.go.core.model

/** UI-safe snapshot of workspace sync state. */
data class SyncStatusSummary(
    val state: SyncStatusState,
    val branch: String?,
    val changedCount: Int,
    val aheadCount: Int,
    val behindCount: Int,
    val message: String
) {
    companion object {
        val unavailable = SyncStatusSummary(
            state = SyncStatusState.Unavailable,
            branch = null,
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Workspace is not available for sync."
        )

        val error = SyncStatusSummary(
            state = SyncStatusState.Error,
            branch = null,
            changedCount = 0,
            aheadCount = 0,
            behindCount = 0,
            message = "Could not read sync status."
        )
    }
}

/** True when the workspace is out of sync and the shell should draw attention. */
val SyncStatusSummary.needsAttention: Boolean
    get() = state != SyncStatusState.Clean && state != SyncStatusState.Unavailable

/** Short label for dashboard and shell diagnostics. */
fun SyncStatusSummary.displayLabel(): String = when (state) {
    SyncStatusState.Clean -> "Clean"
    SyncStatusState.DirtyLocalChanges -> "Local changes"
    SyncStatusState.Ahead -> "Ahead ($aheadCount)"
    SyncStatusState.Behind -> "Behind ($behindCount)"
    SyncStatusState.Diverged -> "Diverged"
    SyncStatusState.ConflictRisk -> "Conflict risk"
    SyncStatusState.Unavailable -> "Not configured"
    SyncStatusState.Error -> "Error"
}
