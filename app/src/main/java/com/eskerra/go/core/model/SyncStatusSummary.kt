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
