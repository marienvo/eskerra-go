package com.eskerra.go.core.repository

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.MergeOutcome
import com.eskerra.go.core.model.RemoteBranchComparison
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncStatusSummary
import java.io.File

/**
 * Git sync operations for manual remote sync. All network auth uses an in-memory
 * token; tokens must never appear in URLs or `.git/config`.
 */
interface RemoteSyncRepository {
    fun status(workingDir: File): Result<GitWorkspaceStatus>

    /** Paths staged in the index (cached vs HEAD). */
    fun readStagedPaths(workingDir: File): Result<Set<String>>

    /** True when merge, rebase, cherry-pick, or revert is in progress. */
    fun requiresManualIntervention(workingDir: File): Boolean

    fun partitionChanges(changedPaths: Set<String>): SyncChangePartition

    fun stageInboxChanges(workingDir: File): Result<Unit>

    /** Stages every working-tree change (adds, edits, deletes); skips `.git` internals. */
    fun stageAllChanges(workingDir: File): Result<Unit> = stageInboxChanges(workingDir)

    /**
     * Aborts any in-progress merge, rebase, cherry-pick, or revert, restoring a clean
     * HEAD so sync can proceed instead of refusing. A no-op when nothing is in progress.
     */
    fun abortInProgressOperation(workingDir: File): Result<Unit> = Result.success(Unit)

    /**
     * Merges `origin/[branch]` into the current branch and always ends conflict-free.
     * Non-conflicting changes merge normally. For each conflicting path the local
     * ("ours") version is saved to a sidecar copy named with [conflictLabel] and the
     * remote ("theirs") version becomes canonical (remote-wins).
     */
    fun mergeRemote(
        workingDir: File,
        branch: String,
        conflictLabel: String
    ): Result<MergeOutcome> = Result.success(MergeOutcome(merged = false))

    /** Stages the given repo-relative [relativePaths] (additions, edits, and deletions). */
    fun stagePaths(workingDir: File, relativePaths: Set<String>): Result<Unit>

    fun commitStaged(workingDir: File, message: String): Result<String>

    fun fetch(workingDir: File, httpsToken: String?): Result<Unit>

    /**
     * Ensures [branch] exists locally and is checked out, fetching from `origin` when
     * a tracking branch must be created from [origin]/[branch].
     */
    /** Returns the branch that was checked out (may differ from [branch] after reconciliation). */
    fun ensureLocalBranch(workingDir: File, branch: String, httpsToken: String?): Result<String>

    fun compareWithRemote(workingDir: File, branch: String): Result<RemoteBranchComparison>

    fun fastForwardToRemote(workingDir: File, branch: String): Result<Unit>

    fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit>

    fun buildStatusSummary(
        workspaceStatus: GitWorkspaceStatus,
        comparison: RemoteBranchComparison?
    ): SyncStatusSummary

    /** Configure or update `origin` with a sanitized URL (no embedded credentials). */
    fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit>

    /**
     * Read-only remote probe via ls-remote. Does not open the local repo for fetch,
     * does not change `.git/config`, and does not update remote-tracking refs.
     */
    fun probeRemoteConnection(remoteUri: String, branch: String, httpsToken: String?): Result<Unit>

    /** Removes sanitized `origin` metadata from the local repository. */
    fun clearSanitizedOrigin(workingDir: File): Result<Unit>

    /** Returns the sanitized `origin` URL from `.git/config`, or null when unset. */
    fun readOriginUrl(workingDir: File): Result<String?>
}
