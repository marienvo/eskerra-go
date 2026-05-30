package com.eskerra.go.core.model

/** Partition of working-tree changes for sync write-boundary checks. */
data class SyncChangePartition(
    val inboxPaths: Set<String>,
    val nonInboxPaths: Set<String>,
    val unsafePaths: Set<String>
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
