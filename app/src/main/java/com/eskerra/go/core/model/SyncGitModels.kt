package com.eskerra.go.core.model

/** Partition of working-tree changes for sync write-boundary checks. */
data class SyncChangePartition(
    val inboxPaths: Set<String>,
    val nonInboxPaths: Set<String>,
    val unsafePaths: Set<String>
)

/**
 * Outcome of merging `origin/<branch>` into the local branch.
 *
 * [merged] is true when a fast-forward or merge commit advanced the local branch.
 * [conflictCopies] holds repo-relative paths of sidecar copies written to preserve
 * the local ("ours") version of each conflicting file before the remote version
 * was taken as canonical (remote-wins policy).
 */
data class MergeOutcome(
    val merged: Boolean,
    val conflictCopies: List<String> = emptyList()
)

/** Comparison between local HEAD and `origin/<branch>` after fetch. */
data class RemoteBranchComparison(
    val aheadCount: Int,
    val behindCount: Int,
    val isEqual: Boolean,
    val localIsAncestorOfRemote: Boolean,
    val remoteIsAncestorOfLocal: Boolean,
    val isDiverged: Boolean,
    val remoteBranchMissing: Boolean = false
)
