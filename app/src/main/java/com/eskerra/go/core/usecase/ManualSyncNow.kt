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
import kotlinx.coroutines.sync.Mutex
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
    private val reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val syncMutex = Mutex()

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (SyncProgressStep) -> Unit = {}
    ): Result<SyncResult> = withContext(dispatcher) {
        if (!syncMutex.tryLock()) {
            return@withContext Result.failure(SyncException(SyncError.SyncAlreadyRunning))
        }
        try {
            val resolvedConfig = reconcileWorkspaceSyncBranch
                ?.invoke(config, filesDir)
                ?.getOrElse { return@withContext Result.failure(it) }
                ?: config
            sync(resolvedConfig, filesDir, onProgress).map { result ->
                if (resolvedConfig != config) {
                    result.copy(updatedConfig = resolvedConfig)
                } else {
                    result
                }
            }
        } finally {
            syncMutex.unlock()
        }
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

        if (remoteSyncRepository.requiresManualIntervention(workspaceDir)) {
            return Result.failure(SyncException(SyncError.ManualInterventionRequired))
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

        validateWorkingTreePaths(workspaceDir, workspaceStatus.changedPaths).getOrElse { error ->
            return Result.failure(error)
        }

        val partition = remoteSyncRepository.partitionChanges(workspaceStatus.changedPaths)

        val effectiveBranch = remoteSyncRepository
            .ensureLocalBranch(workspaceDir, branch, httpsToken)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

        var committed = false
        var commitId: String? = null
        if (partition.inboxPaths.isNotEmpty()) {
            validateWorkingTreePaths(
                workspaceDir,
                remoteSyncRepository.status(workspaceDir).getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
                }.changedPaths
            ).getOrElse { error ->
                return Result.failure(error)
            }

            onProgress(SyncProgressStep.CommittingInboxChanges)
            remoteSyncRepository.stageInboxChanges(workspaceDir).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

            validateStagedPaths(workspaceDir).getOrElse { error ->
                return Result.failure(error)
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
            .compareWithRemote(workspaceDir, effectiveBranch)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
            }
        if (comparison.remoteBranchMissing) {
            return Result.failure(SyncException(SyncError.RemoteBranchNotFound(effectiveBranch)))
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
            remoteSyncRepository
                .fastForwardToRemote(workspaceDir, effectiveBranch)
                .getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                }
            pulled = true
        }

        var pushed = false
        val postIntegration = remoteSyncRepository.compareWithRemote(workspaceDir, effectiveBranch)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
            }

        if (postIntegration.remoteIsAncestorOfLocal && !postIntegration.isEqual) {
            onProgress(SyncProgressStep.PushingLocalCommits)
            remoteSyncRepository
                .push(workspaceDir, effectiveBranch, httpsToken)
                .getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                }
            pushed = true
            remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
            }
        }

        onProgress(SyncProgressStep.RefreshingNotes)
        val registryRefreshed = registryRepository.refresh(config, filesDir).isSuccess

        onProgress(SyncProgressStep.Complete)
        val status = loadSyncStatus(config, filesDir)
        return Result.success(
            SyncResult(
                status = status,
                committed = committed,
                commitId = commitId,
                pushed = pushed,
                pulled = pulled,
                registryRefreshed = registryRefreshed
            )
        )
    }

    private fun validateWorkingTreePaths(
        workspaceDir: File,
        changedPaths: Set<String>
    ): Result<Unit> {
        validateStagedPaths(workspaceDir).getOrElse { return Result.failure(it) }
        val partition = remoteSyncRepository.partitionChanges(changedPaths)
        if (partition.unsafePaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.UnsafeLocalPath))
        }
        if (partition.nonInboxPaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.NonInboxLocalChanges))
        }
        return Result.success(Unit)
    }

    private fun validateStagedPaths(workspaceDir: File): Result<Unit> {
        val stagedPaths = remoteSyncRepository.readStagedPaths(workspaceDir).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error))
        }
        val stagedPartition = remoteSyncRepository.partitionChanges(stagedPaths)
        if (stagedPartition.unsafePaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.UnsafeLocalPath))
        }
        if (stagedPartition.nonInboxPaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.NonInboxStagedChanges))
        }
        return Result.success(Unit)
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
