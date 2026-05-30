package com.eskerra.go.data.git

/**
 * Minimal snapshot of a working tree's Git status for the Step 2 spike.
 *
 * @property branch the currently checked-out branch name (or detached HEAD id).
 * @property hasUncommittedChanges true when the working tree is not clean.
 * @property changedPaths repo-relative paths that differ from HEAD/index
 *   (added, changed, modified, removed, missing, or untracked).
 */
data class GitWorkspaceStatus(
    val branch: String,
    val hasUncommittedChanges: Boolean,
    val changedPaths: Set<String>,
)
