package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.git.GitBranchNameValidator
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads sync status from local Git state without network writes. */
class LoadSyncStatus(
    private val remoteSyncRepository: RemoteSyncRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): SyncStatusSummary =
        withContext(dispatcher) {
            load(config, filesDir)
        }

    private fun load(config: WorkspaceConfig, filesDir: File): SyncStatusSummary {
        val workspaceDir = resolveWorkspace(config, filesDir)
            ?: return SyncStatusSummary.unavailable

        val remoteUri = config.remoteUri?.trim().orEmpty()
        if (remoteUri.isBlank()) {
            return SyncStatusSummary(
                state = SyncStatusState.Unavailable,
                branch = config.branch.takeIf { it.isNotBlank() },
                changedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                message = SyncError.MissingRemoteConfig.message()
            )
        }
        if (RemoteUriSecurity.containsEmbeddedCredentials(remoteUri)) {
            return statusWithMessage(SyncError.InvalidRemoteUri.message(), config.branch)
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(remoteUri)) {
            return statusWithMessage(SyncError.UnsupportedRemoteScheme.message(), config.branch)
        }

        val branch = config.branch.trim()
        if (branch.isBlank() || GitBranchNameValidator.validate(branch).isFailure) {
            return SyncStatusSummary.error
        }

        val workspaceStatus = remoteSyncRepository.status(workspaceDir).getOrNull()
            ?: return SyncStatusSummary.error

        val comparison = remoteSyncRepository.compareWithRemote(workspaceDir, branch)
            .getOrNull()

        if (comparison?.remoteBranchMissing == true) {
            return statusWithMessage(SyncError.RemoteBranchNotFound(branch).message(), branch)
        }

        return remoteSyncRepository.buildStatusSummary(workspaceStatus, comparison)
    }

    private fun statusWithMessage(message: String, branch: String?) = SyncStatusSummary(
        state = SyncStatusState.Unavailable,
        branch = branch,
        changedCount = 0,
        aheadCount = 0,
        behindCount = 0,
        message = message
    )

    private fun resolveWorkspace(config: WorkspaceConfig, filesDir: File): File? {
        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()
            ?: return null
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return null
        }
        return workspaceDir
    }
}
