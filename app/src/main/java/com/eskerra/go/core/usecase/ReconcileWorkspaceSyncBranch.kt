package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.SyncGitErrorMapper
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.data.workspace.WorkspaceStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aligns persisted [WorkspaceConfig.branch] with the remote when a legacy `master`
 * config points at a `main`-only remote, and checks out the effective branch.
 */
class ReconcileWorkspaceSyncBranch(
    private val workspaceStore: WorkspaceStore,
    private val credentialStore: CredentialStore,
    private val remoteSyncRepository: RemoteSyncRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): Result<WorkspaceConfig> =
        withContext(dispatcher) {
            reconcile(config, filesDir)
        }

    private suspend fun reconcile(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<WorkspaceConfig> {
        val remoteUri = config.remoteUri?.trim().orEmpty()
        if (remoteUri.isBlank()) {
            return Result.success(config)
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(remoteUri)) {
            return Result.success(config)
        }

        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()
            ?: return Result.success(config)
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return Result.success(config)
        }

        val branch = config.branch.trim()
        if (branch.isBlank()) {
            return Result.success(config)
        }

        val httpsToken = readHttpsToken(config, remoteUri).getOrElse { error ->
            return error as Result<WorkspaceConfig>
        }

        val effectiveBranch = remoteSyncRepository
            .ensureLocalBranch(workspaceDir, branch, httpsToken)
            .getOrElse { error ->
                return Result.failure(SyncGitErrorMapper.mapFailure(error, branch))
            }

        if (effectiveBranch == branch) {
            return Result.success(config)
        }

        val updated = config.copy(branch = effectiveBranch)
        return try {
            workspaceStore.save(updated)
            Result.success(updated)
        } catch (_: Exception) {
            Result.failure(
                SyncException(SyncError.GitFailed("Could not save reconciled branch name."))
            )
        }
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
}
