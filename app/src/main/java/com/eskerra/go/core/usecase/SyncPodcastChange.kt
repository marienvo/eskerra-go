package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.SyncGitErrorMapper
import com.eskerra.go.data.git.SyncPathClassifier
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Commits and (when possible) pushes pending podcast markdown changes under
 * `General/` as one isolated commit, separate from the manual inbox sync channel.
 *
 * Behavior contract (see sync-hardening spec):
 * - Shares one [GitSyncMutex] with [ManualSyncNow] so git operations never overlap.
 * - Stages only auto-managed podcast paths ([SyncPathClassifier.isPodcastPath]).
 * - Always commits locally first; the commit is the source of truth.
 * - fetch + fast-forward-only + push when a reachable remote allows it.
 * - Offline, missing remote, push rejection, or divergence never fail the result;
 *   they leave a local commit with [PodcastSyncResult.pendingPush] = true to retry.
 * - Hard repository problems (missing workspace, in-progress merge, commit failure,
 *   unexpected staged files) return [Result.failure].
 */
class SyncPodcastChange(
    private val remoteSyncRepository: RemoteSyncRepository,
    private val credentialStore: CredentialStore,
    private val gitSyncMutex: GitSyncMutex,
    private val commitMessage: String = PODCAST_COMMIT_MESSAGE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<PodcastSyncResult> = withContext(dispatcher) {
        gitSyncMutex.mutex.withLock {
            sync(config, filesDir)
        }
    }

    private suspend fun sync(config: WorkspaceConfig, filesDir: File): Result<PodcastSyncResult> {
        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrElse {
            return Result.failure(SyncException(SyncError.WorkspaceUnavailable))
        }
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return Result.failure(SyncException(SyncError.WorkspaceUnavailable))
        }
        if (remoteSyncRepository.requiresManualIntervention(workspaceDir)) {
            return Result.failure(SyncException(SyncError.ManualInterventionRequired))
        }

        val status = remoteSyncRepository.status(workspaceDir).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error))
        }
        val podcastPaths = status.changedPaths
            .filter { SyncPathClassifier.isPodcastPath(it) }
            .toSet()
        if (podcastPaths.isEmpty()) {
            return Result.success(PodcastSyncResult.NOTHING_TO_COMMIT)
        }

        remoteSyncRepository.stagePaths(workspaceDir, podcastPaths).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error))
        }

        val stagedPaths = remoteSyncRepository.readStagedPaths(workspaceDir).getOrElse { error ->
            return Result.failure(SyncGitErrorMapper.mapFailure(error))
        }
        if (stagedPaths.any { !SyncPathClassifier.isPodcastPath(it) }) {
            return Result.failure(SyncException(SyncError.UnexpectedStagedChanges))
        }

        val commitId = remoteSyncRepository
            .commitStaged(workspaceDir, commitMessage)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error))
            }

        val pushOutcome = tryPushCommit(config, workspaceDir)
        return Result.success(
            PodcastSyncResult(
                committed = true,
                commitId = commitId,
                pushed = pushOutcome == PushOutcome.PUSHED,
                pendingPush = pushOutcome == PushOutcome.PENDING
            )
        )
    }

    /**
     * Best-effort fetch + fast-forward + push of the local commit.
     *
     * - [PushOutcome.NO_REMOTE]: no remote is configured; nothing to push.
     * - [PushOutcome.PUSHED]: the commit reached the remote.
     * - [PushOutcome.PENDING]: a remote is intended but unreachable, mis-configured,
     *   or diverged; the local commit stays for a later retry.
     */
    private suspend fun tryPushCommit(config: WorkspaceConfig, workspaceDir: File): PushOutcome {
        val remoteUri = config.remoteUri?.trim().orEmpty()
        if (remoteUri.isBlank()) return PushOutcome.NO_REMOTE
        if (RemoteUriSecurity.validateNoEmbeddedCredentials(remoteUri).isFailure) {
            return PushOutcome.PENDING
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(remoteUri)) return PushOutcome.PENDING

        val branch = config.branch.trim()
        if (branch.isEmpty()) return PushOutcome.PENDING

        val httpsToken = if (remoteUri.startsWith("https://", ignoreCase = true)) {
            val token = credentialStore.readToken(config.relativePath).getOrNull()
            if (token.isNullOrBlank()) return PushOutcome.PENDING
            token
        } else {
            null
        }

        return runCatching {
            remoteSyncRepository.fetch(workspaceDir, httpsToken).getOrThrow()

            val comparison = remoteSyncRepository
                .compareWithRemote(workspaceDir, branch)
                .getOrThrow()
            if (comparison.remoteBranchMissing || comparison.isDiverged) {
                return PushOutcome.PENDING
            }
            if (comparison.aheadCount > 0 && comparison.behindCount > 0) {
                return PushOutcome.PENDING
            }

            if (comparison.localIsAncestorOfRemote && !comparison.isEqual) {
                remoteSyncRepository.fastForwardToRemote(workspaceDir, branch).getOrThrow()
            }

            val postIntegration = remoteSyncRepository
                .compareWithRemote(workspaceDir, branch)
                .getOrThrow()
            if (postIntegration.remoteIsAncestorOfLocal && !postIntegration.isEqual) {
                remoteSyncRepository.push(workspaceDir, branch, httpsToken).getOrThrow()
            }
            PushOutcome.PUSHED
        }.getOrDefault(PushOutcome.PENDING)
    }

    private enum class PushOutcome { PUSHED, PENDING, NO_REMOTE }

    companion object {
        const val PODCAST_COMMIT_MESSAGE = "Update podcasts from Eskerra Go"
    }
}
