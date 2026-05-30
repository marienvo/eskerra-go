package com.eskerra.go.core.repository

import com.eskerra.go.core.model.GitWorkspaceStatus
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

    fun partitionChanges(changedPaths: Set<String>): SyncChangePartition

    fun stageInboxChanges(workingDir: File): Result<Unit>

    fun commitStaged(workingDir: File, message: String): Result<String>

    fun fetch(workingDir: File, httpsToken: String?): Result<Unit>

    /**
     * Ensures [branch] exists locally and is checked out, fetching from `origin` when
     * a tracking branch must be created from [origin]/[branch].
     */
    fun ensureLocalBranch(workingDir: File, branch: String, httpsToken: String?): Result<Unit>

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
