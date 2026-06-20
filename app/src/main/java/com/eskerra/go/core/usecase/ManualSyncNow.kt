package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentCachePort
import com.eskerra.go.core.repository.NoteRegistryCachePort
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitBranchNameValidator
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.SyncGitErrorMapper
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val registryCache: NoteRegistryCachePort,
    private val contentCache: NoteContentCachePort? = null,
    private val loadSyncStatus: LoadSyncStatus,
    private val reconcileWorkspaceSyncBranch: ReconcileWorkspaceSyncBranch? = null,
    private val gitSyncMutex: GitSyncMutex = GitSyncMutex(),
    private val clock: () -> Instant = Instant::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (SyncProgressStep) -> Unit = {}
    ): Result<SyncResult> = withContext(dispatcher) {
        if (!gitSyncMutex.mutex.tryLock()) {
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
            gitSyncMutex.mutex.unlock()
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

        // Recover from a half-finished Git operation instead of refusing to sync.
        if (remoteSyncRepository.requiresManualIntervention(workspaceDir)) {
            remoteSyncRepository.abortInProgressOperation(workspaceDir).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error))
            }
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

        // The only hard stop left: genuinely unsafe paths (.git internals, `..`).
        // Everything else is committed and synced unconditionally.
        ensureNoUnsafePaths(workspaceStatus.changedPaths).getOrElse { error ->
            return Result.failure(error)
        }

        val effectiveBranch = remoteSyncRepository
            .ensureLocalBranch(workspaceDir, branch, httpsToken)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

        var committed = false
        var commitId: String? = null
        if (workspaceStatus.hasUncommittedChanges) {
            onProgress(SyncProgressStep.CommittingInboxChanges)
            remoteSyncRepository.stageAllChanges(workspaceDir).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            ensureNoUnsafeStaged(workspaceDir).getOrElse { error ->
                return Result.failure(error)
            }
            commitId = remoteSyncRepository.commitStaged(
                workingDir = workspaceDir,
                message = LOCAL_COMMIT_MESSAGE
            ).getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }
            committed = true
        }

        onProgress(SyncProgressStep.FetchingRemote)
        remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
        }

        var pulled = false
        var pushed = false
        val conflictCopies = mutableListOf<String>()

        var attempt = 0
        while (true) {
            attempt++
            val comparison = remoteSyncRepository
                .compareWithRemote(workspaceDir, effectiveBranch)
                .getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                }
            if (comparison.remoteBranchMissing) {
                return Result.failure(
                    SyncException(SyncError.RemoteBranchNotFound(effectiveBranch))
                )
            }

            // Integrate remote into local: fast-forward when purely behind, otherwise
            // merge (saving local copies of any conflicts; remote wins the canonical file).
            if (comparison.localIsAncestorOfRemote && !comparison.isEqual) {
                onProgress(SyncProgressStep.IntegratingRemote)
                remoteSyncRepository
                    .fastForwardToRemote(workspaceDir, effectiveBranch)
                    .getOrElse { error ->
                        return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                    }
                pulled = true
            } else if (comparison.isDiverged ||
                (comparison.aheadCount > 0 && comparison.behindCount > 0)
            ) {
                onProgress(SyncProgressStep.IntegratingRemote)
                val outcome = remoteSyncRepository
                    .mergeRemote(workspaceDir, effectiveBranch, conflictLabel())
                    .getOrElse { error ->
                        return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                    }
                pulled = true
                conflictCopies += outcome.conflictCopies
            }

            val postIntegration = remoteSyncRepository
                .compareWithRemote(workspaceDir, effectiveBranch)
                .getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                }

            if (postIntegration.remoteIsAncestorOfLocal && !postIntegration.isEqual) {
                onProgress(SyncProgressStep.PushingLocalCommits)
                val pushResult =
                    remoteSyncRepository.push(workspaceDir, effectiveBranch, httpsToken)
                if (pushResult.isFailure) {
                    val mapped = SyncGitErrorMapper.mapFailure(
                        pushResult.exceptionOrNull() ?: RuntimeException("push failed"),
                        effectiveBranch
                    )
                    // A rejected push means the remote advanced mid-sync: re-fetch and
                    // re-integrate so we still end up pushed, no matter what.
                    if (mapped.error is SyncError.PushRejected && attempt < MAX_PUSH_ATTEMPTS) {
                        remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
                            return Result.failure(
                                SyncGitErrorMapper.mapFailure(error, effectiveBranch)
                            )
                        }
                        continue
                    }
                    return Result.failure(mapped)
                }
                pushed = true
                remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrElse { error ->
                    return Result.failure(SyncGitErrorMapper.mapFailure(error, effectiveBranch))
                }
            }
            break
        }

        onProgress(SyncProgressStep.RefreshingNotes)
        contentCache?.evictAll()
        registryCache.invalidate(config, filesDir)
        val registryRefreshed = registryCache.refresh(config, filesDir).isSuccess

        onProgress(SyncProgressStep.Complete)
        val status = loadSyncStatus(config, filesDir)
        return Result.success(
            SyncResult(
                status = status,
                committed = committed,
                commitId = commitId,
                pushed = pushed,
                pulled = pulled,
                registryRefreshed = registryRefreshed,
                conflictCopies = conflictCopies.toList()
            )
        )
    }

    private fun ensureNoUnsafePaths(changedPaths: Set<String>): Result<Unit> {
        val partition = remoteSyncRepository.partitionChanges(changedPaths)
        if (partition.unsafePaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.UnsafeLocalPath))
        }
        return Result.success(Unit)
    }

    private fun ensureNoUnsafeStaged(workspaceDir: File): Result<Unit> {
        val stagedPaths = remoteSyncRepository.readStagedPaths(workspaceDir).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error))
        }
        val stagedPartition = remoteSyncRepository.partitionChanges(stagedPaths)
        if (stagedPartition.unsafePaths.isNotEmpty()) {
            return Result.failure(SyncException(SyncError.UnsafeLocalPath))
        }
        return Result.success(Unit)
    }

    private fun conflictLabel(): String =
        "conflict " + CONFLICT_TIMESTAMP.format(clock().atZone(zoneId))

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
        const val LOCAL_COMMIT_MESSAGE = "Sync local changes from Eskerra Go"

        /** Kept for backward compatibility; local commits now cover all paths. */
        const val INBOX_COMMIT_MESSAGE = LOCAL_COMMIT_MESSAGE

        /** Max integrate+push cycles before giving up on a racing remote. */
        private const val MAX_PUSH_ATTEMPTS = 3

        private val CONFLICT_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    }
}
