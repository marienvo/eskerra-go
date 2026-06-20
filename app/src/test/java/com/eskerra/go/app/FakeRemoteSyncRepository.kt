package com.eskerra.go.app

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.RemoteBranchComparison
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.RemoteSyncRepository
import java.io.File

class FakeRemoteSyncRepository(
    private val fixedStatus: SyncStatusSummary = SyncStatusSummary(
        state = SyncStatusState.Clean,
        branch = "main",
        changedCount = 0,
        aheadCount = 0,
        behindCount = 0,
        message = "Up to date."
    )
) : RemoteSyncRepository {

    override fun status(workingDir: File): Result<GitWorkspaceStatus> = Result.success(
        GitWorkspaceStatus(
            branch = fixedStatus.branch.orEmpty(),
            hasUncommittedChanges = fixedStatus.changedCount > 0,
            changedPaths = emptySet()
        )
    )

    override fun readStagedPaths(workingDir: File): Result<Set<String>> = Result.success(emptySet())

    override fun requiresManualIntervention(workingDir: File): Boolean = false

    override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
        SyncChangePartition(emptySet(), emptySet(), emptySet())

    override fun stageInboxChanges(workingDir: File): Result<Unit> = Result.success(Unit)

    override fun commitStaged(workingDir: File, message: String): Result<String> =
        Result.success("abc123")

    override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> = Result.success(Unit)

    override fun ensureLocalBranch(
        workingDir: File,
        branch: String,
        httpsToken: String?
    ): Result<String> = Result.success(branch)

    override fun compareWithRemote(
        workingDir: File,
        branch: String
    ): Result<RemoteBranchComparison> = Result.success(
        RemoteBranchComparison(
            aheadCount = 0,
            behindCount = 0,
            isEqual = true,
            localIsAncestorOfRemote = true,
            remoteIsAncestorOfLocal = true,
            isDiverged = false
        )
    )

    override fun fastForwardToRemote(workingDir: File, branch: String): Result<Unit> =
        Result.success(Unit)

    override fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit> =
        Result.success(Unit)

    override fun buildStatusSummary(
        workspaceStatus: GitWorkspaceStatus,
        comparison: RemoteBranchComparison?
    ): SyncStatusSummary = fixedStatus

    override fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit> =
        Result.success(Unit)

    override fun probeRemoteConnection(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<Unit> = Result.success(Unit)

    override fun clearSanitizedOrigin(workingDir: File): Result<Unit> = Result.success(Unit)

    override fun readOriginUrl(workingDir: File): Result<String?> = Result.success(null)
}

class FakeRegistryRepository : NoteRegistryRepository {
    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry?
    ): Result<NoteRegistry> = Result.success(NoteRegistry.fromNotes(emptyList()))
}
