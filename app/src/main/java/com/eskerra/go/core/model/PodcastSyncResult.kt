package com.eskerra.go.core.model

/**
 * Outcome of a single podcast auto-sync attempt.
 *
 * The local commit is the source of truth: a successful result with
 * [pendingPush] = true means the change was committed but could not be pushed yet
 * (offline, no remote reachable, or remote diverged). The next podcast change or
 * manual inbox sync will push the outstanding commits.
 */
data class PodcastSyncResult(
    val committed: Boolean,
    val commitId: String?,
    val pushed: Boolean,
    val pendingPush: Boolean
) {
    companion object {
        val NOTHING_TO_COMMIT = PodcastSyncResult(
            committed = false,
            commitId = null,
            pushed = false,
            pendingPush = false
        )
    }
}
