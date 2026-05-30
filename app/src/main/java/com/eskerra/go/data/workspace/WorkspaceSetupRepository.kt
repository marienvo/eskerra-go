package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.WorkspaceGitRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orchestrates Step 2 Git operations for the fixed workspace directory. */
interface WorkspaceSetupRepository {
    suspend fun completeSetup(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        filesDir: File
    ): Result<WorkspaceConfig>
}

class DefaultWorkspaceSetupRepository(private val gitRepository: WorkspaceGitRepository) :
    WorkspaceSetupRepository {

    override suspend fun completeSetup(
        mode: WorkspaceSetupMode,
        name: String,
        branch: String,
        remoteUri: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        val trimmedBranch = branch.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.BlankName)
            )
        }
        val workspaceDirResult = WorkspacePaths.resolve(
            filesDir,
            WorkspacePaths.DEFAULT_RELATIVE_PATH
        )
        val workspaceDir = workspaceDirResult.getOrElse { error ->
            return@withContext Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed(error.message))
            )
        }

        when (mode) {
            WorkspaceSetupMode.Clone -> {
                if (trimmedBranch.isBlank()) {
                    return@withContext Result.failure(
                        WorkspaceSetupException(WorkspaceSetupError.BlankBranch)
                    )
                }
                completeClone(trimmedName, trimmedBranch, remoteUri, workspaceDir)
            }
            WorkspaceSetupMode.InitializeLocal -> completeInit(trimmedName, workspaceDir)
        }
    }

    private fun completeClone(
        name: String,
        branch: String,
        remoteUri: String?,
        workspaceDir: File
    ): Result<WorkspaceConfig> {
        val uri = remoteUri?.trim().orEmpty()
        if (uri.isBlank()) {
            return Result.failure(WorkspaceSetupException(WorkspaceSetupError.BlankRemoteUri))
        }
        RemoteUriSecurity.validateNoEmbeddedCredentials(uri).getOrElse { error ->
            return Result.failure(error)
        }
        if (!isFileRemoteUri(uri)) {
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.UnsupportedRemoteScheme)
            )
        }

        WorkspacePaths.ensureEmptyDirectory(workspaceDir).getOrElse { error ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed(error.message))
            )
        }

        gitRepository.cloneFrom(uri, workspaceDir, branch).getOrElse { error ->
            cleanupOnFailure(workspaceDir)
            return Result.failure(mapCloneFailure(error, branch))
        }

        return buildConfig(name, uri, workspaceDir, validateBranch = branch).also { result ->
            if (result.isFailure) cleanupOnFailure(workspaceDir)
        }
    }

    private fun completeInit(name: String, workspaceDir: File): Result<WorkspaceConfig> {
        WorkspacePaths.ensureEmptyDirectory(workspaceDir).getOrElse { error ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed(error.message))
            )
        }

        gitRepository.initOrOpen(workspaceDir).getOrElse { error ->
            cleanupOnFailure(workspaceDir)
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.InitFailed(error.message))
            )
        }

        return buildConfig(
            name,
            remoteUri = null,
            workspaceDir,
            validateBranch = null
        ).also { result ->
            if (result.isFailure) cleanupOnFailure(workspaceDir)
        }
    }

    private fun buildConfig(
        name: String,
        remoteUri: String?,
        workspaceDir: File,
        validateBranch: String?
    ): Result<WorkspaceConfig> {
        val status = gitRepository.status(workspaceDir).getOrElse { error ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed(error.message))
            )
        }
        if (validateBranch != null && status.branch != validateBranch) {
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.BranchNotFound(validateBranch))
            )
        }
        return Result.success(
            WorkspaceConfig(
                name = name,
                relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
                remoteUri = remoteUri,
                branch = status.branch,
                setupCompletedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun cleanupOnFailure(workspaceDir: File) {
        if (workspaceDir.exists()) {
            workspaceDir.deleteRecursively()
        }
    }

    private fun isFileRemoteUri(uri: String): Boolean =
        uri.startsWith("file://") || uri.startsWith("file:/")
}
