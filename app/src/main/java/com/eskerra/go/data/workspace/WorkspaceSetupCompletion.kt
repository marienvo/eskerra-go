package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.CredentialStore

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
    private val credentialStore: CredentialStore
) : WorkspaceSetupCompletion {

    override suspend fun completeAndPersist(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String?,
        filesDir: java.io.File
    ): Result<WorkspaceConfig> {
        val config = setupRepository.completeSetup(
            mode = mode,
            name = name,
            branch = branch,
            remoteUri = remoteUri,
            filesDir = filesDir
        ).getOrElse { return Result.failure(it) }

        val trimmedCredential = credential?.trim().orEmpty()
        var credentialWasSaved = false
        if (trimmedCredential.isNotEmpty()) {
            credentialStore.saveToken(config.relativePath, trimmedCredential).getOrElse { error ->
                return Result.failure(
                    WorkspaceSetupException(
                        WorkspaceSetupError.CredentialSaveFailed(error.message)
                    )
                )
            }
            credentialWasSaved = true
        } else {
            credentialStore.clear(config.relativePath).getOrElse { error ->
                return Result.failure(
                    WorkspaceSetupException(
                        WorkspaceSetupError.CredentialSaveFailed(error.message)
                    )
                )
            }
        }

        return try {
            workspaceStore.save(config)
            Result.success(config)
        } catch (error: Exception) {
            if (credentialWasSaved) {
                credentialStore.clear(config.relativePath)
            }
            Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.MetadataSaveFailed(error.message))
            )
        }
    }
}
