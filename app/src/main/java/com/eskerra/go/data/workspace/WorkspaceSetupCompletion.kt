package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import kotlinx.coroutines.sync.withLock

/**
 * Completes Git setup and persists workspace metadata plus optional credentials.
 * Persistence failures are returned to the setup UI; the gate must not advance on failure.
 */
interface WorkspaceSetupCompletion {
    suspend fun completeAndPersist(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String?,
        filesDir: java.io.File
    ): Result<WorkspaceConfig>
}

class DefaultWorkspaceSetupCompletion(
    private val setupRepository: WorkspaceSetupRepository,
    private val workspaceStore: WorkspaceStore,
    private val credentialStore: CredentialStore,
    private val gitSyncMutex: GitSyncMutex = GitSyncMutex()
) : WorkspaceSetupCompletion {

    override suspend fun completeAndPersist(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String?,
        filesDir: java.io.File
    ): Result<WorkspaceConfig> = gitSyncMutex.mutex.withLock {
        val config = setupRepository.completeSetup(
            mode = mode,
            name = name,
            branch = branch,
            remoteUri = remoteUri,
            credential = credential,
            filesDir = filesDir
        ).getOrElse { return@withLock Result.failure(it) }

        val trimmedCredential = if (mode == WorkspaceSetupMode.Clone) {
            credential?.trim().orEmpty()
        } else {
            ""
        }
        var credentialWasSaved = false
        if (trimmedCredential.isNotEmpty()) {
            credentialStore.saveToken(config.relativePath, trimmedCredential).getOrElse { _ ->
                rollbackWorkspace(filesDir, config)
                return@withLock Result.failure(
                    WorkspaceSetupException(WorkspaceSetupError.CredentialSaveFailed)
                )
            }
            credentialWasSaved = true
        } else {
            credentialStore.clear(config.relativePath).getOrElse { _ ->
                rollbackWorkspace(filesDir, config)
                return@withLock Result.failure(
                    WorkspaceSetupException(WorkspaceSetupError.CredentialSaveFailed)
                )
            }
        }

        try {
            workspaceStore.save(config)
            Result.success(config)
        } catch (error: Exception) {
            rollbackAfterMetadataSaveFailure(filesDir, config, credentialWasSaved)
            Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.MetadataSaveFailed)
            )
        }
    }

    private suspend fun rollbackAfterMetadataSaveFailure(
        filesDir: java.io.File,
        config: WorkspaceConfig,
        credentialWasSaved: Boolean
    ) {
        if (credentialWasSaved) {
            credentialStore.clear(config.relativePath)
        }
        rollbackWorkspace(filesDir, config)
    }

    private fun rollbackWorkspace(filesDir: java.io.File, config: WorkspaceConfig) {
        WorkspacePaths.removeWorkspaceDirectory(filesDir, config.relativePath)
    }
}
