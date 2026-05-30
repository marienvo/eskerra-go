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

    fun compareWithRemote(workingDir: File, branch: String): Result<RemoteBranchComparison>

    fun fastForwardToRemote(workingDir: File, branch: String): Result<Unit>

    fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit>

    fun buildStatusSummary(
        workspaceStatus: GitWorkspaceStatus,
        comparison: RemoteBranchComparison?
    ): SyncStatusSummary
}
