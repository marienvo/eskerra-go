package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitBranchNameValidator
import com.eskerra.go.data.git.SyncGitErrorMapper
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates explicit user-triggered manual Git sync for a configured remote
 * workspace. See Step 9 sync algorithm in the project plan.
 */
class ManualSyncNow(
    private val remoteSyncRepository: RemoteSyncRepository,
    private val credentialStore: CredentialStore,
    private val registryRepository: NoteRegistryRepository,
    private val loadSyncStatus: LoadSyncStatus,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (SyncProgressStep) -> Unit = {}
    ): Result<SyncResult> = withContext(dispatcher) {
        sync(config, filesDir, onProgress)
    }

    private suspend fun sync(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (SyncProgressStep) -> Unit
    ): Result<SyncResult> {
        onProgress(SyncProgressStep.ValidatingWorkspace)

        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrElse {
            return Result.failure(SyncException(SyncError.WorkspaceUnavailable))
        }
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return Result.failure(SyncException(SyncError.WorkspaceUnavailable))
        }

        val remoteUri = config.remoteUri?.trim().orEmpty()
        if (remoteUri.isBlank()) {
            return Result.failure(SyncException(SyncError.MissingRemoteConfig))
        }
        RemoteUriSecurity.validateNoEmbeddedCredentials(remoteUri).getOrElse {
            return Result.failure(SyncException(SyncError.InvalidRemoteUri))
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(remoteUri)) {
            return Result.failure(SyncException(SyncError.UnsupportedRemoteScheme))
        }

        val branch = config.branch.trim()
        GitBranchNameValidator.validate(branch).getOrElse {
            return Result.failure(SyncException(SyncError.InvalidBranch))
        }

        onProgress(SyncProgressStep.ReadingCredentials)
        val httpsToken = readHttpsToken(config, remoteUri).getOrElse { error ->
            return Result.failure(error)
        }

        onProgress(SyncProgressStep.InspectingStatus)
        val workspaceStatus = remoteSyncRepository.status(workspaceDir).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
        }

        val partition = remoteSyncRepository.partitionChanges(workspaceStatus.changedPaths)
        if (partition.unsafePaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.UnsafeLocalPath))
        }
        if (partition.nonInboxPaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.NonInboxLocalChanges))
        }

        remoteSyncRepository
            .ensureLocalBranch(workspaceDir, branch, httpsToken)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

        var committed = false
        var commitId: String? = null
        if (partition.inboxPaths.isNotEmpty()) {
            onProgress(SyncProgressStep.CommittingInboxChanges)
            remoteSyncRepository.stageInboxChanges(workspaceDir).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            commitId = remoteSyncRepository.commitStaged(
                workingDir = workspaceDir,
                message = INBOX_COMMIT_MESSAGE
            ).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            committed = true
        }

        onProgress(SyncProgressStep.FetchingRemote)
        remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
        }

        val comparison = remoteSyncRepository
            .compareWithRemote(workspaceDir, branch)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
        if (comparison.remoteBranchMissing) {
            return Result.failure(SyncException(SyncError.RemoteBranchNotFound(branch)))
        }

        var pulled = false
        if (comparison.isDiverged) {
            return Result.failure(SyncException(SyncError.Diverged))
        }
        if (comparison.aheadCount > 0 && comparison.behindCount > 0) {
            return Result.failure(SyncException(SyncError.ConflictRisk))
        }

        if (comparison.localIsAncestorOfRemote && !comparison.isEqual) {
            onProgress(SyncProgressStep.IntegratingRemote)
            remoteSyncRepository.fastForwardToRemote(workspaceDir, branch).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            pulled = true
        }

        var pushed = false
        val postIntegration = remoteSyncRepository.compareWithRemote(workspaceDir, branch)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

        if (postIntegration.remoteIsAncestorOfLocal && !postIntegration.isEqual) {
            onProgress(SyncProgressStep.PushingLocalCommits)
            remoteSyncRepository.push(workspaceDir, branch, httpsToken).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            pushed = true
            remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
        }

        onProgress(SyncProgressStep.RefreshingNotes)
        registryRepository.refresh(config, filesDir).getOrElse {
            return Result.failure(
                SyncException(SyncError.GitFailed("Could not refresh note registry after sync."))
            )
        }

        onProgress(SyncProgressStep.Complete)
        val status = loadSyncStatus(config, filesDir)
        return Result.success(
            SyncResult(
                status = status,
                committed = committed,
                commitId = commitId,
                pushed = pushed,
                pulled = pulled
            )
        )
    }

    private suspend fun readHttpsToken(
        config: WorkspaceConfig,
        remoteUri: String
    ): Result<String?> {
        if (!remoteUri.startsWith("https://", ignoreCase = true)) {
            return Result.success(null)
        }
        val token = credentialStore.readToken(config.relativePath).getOrElse {
            return Result.failure(SyncException(SyncError.MissingCredential))
        }
        if (token.isNullOrBlank()) {
            return Result.failure(SyncException(SyncError.MissingCredential))
        }
        return Result.success(token)
    }

    companion object {
        const val INBOX_COMMIT_MESSAGE = "Update inbox notes from Eskerra Go"
    }
}
