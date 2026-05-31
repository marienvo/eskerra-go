package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.GitBranchNameValidator
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
        credential: String?,
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
        credential: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.BlankName)
            )
        }
        val workspaceDirResult = WorkspacePaths.resolve(
            filesDir,
            WorkspacePaths.DEFAULT_RELATIVE_PATH
        )
        val workspaceDir = workspaceDirResult.getOrElse { _ ->
            return@withContext Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed)
            )
        }

        when (mode) {
            WorkspaceSetupMode.Clone -> {
                GitBranchNameValidator.validate(branch).getOrElse { error ->
                    return@withContext Result.failure(error)
                }
                completeClone(
                    trimmedName,
                    branch,
                    remoteUri,
                    credential?.trim().orEmpty(),
                    workspaceDir
                )
            }
            WorkspaceSetupMode.InitializeLocal -> completeInit(trimmedName, workspaceDir)
        }
    }

    private fun completeClone(
        name: String,
        branch: String,
        remoteUri: String?,
        credential: String,
        workspaceDir: File
    ): Result<WorkspaceConfig> {
        val uri = remoteUri?.trim().orEmpty()
        if (uri.isBlank()) {
            return Result.failure(WorkspaceSetupException(WorkspaceSetupError.BlankRemoteUri))
        }
        RemoteUriSecurity.validateNoEmbeddedCredentials(uri).getOrElse { error ->
            return Result.failure(error)
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(uri)) {
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.UnsupportedRemoteScheme)
            )
        }
        val httpsToken = if (uri.startsWith("https://", ignoreCase = true)) {
            if (credential.isBlank()) {
                return Result.failure(
                    WorkspaceSetupException(WorkspaceSetupError.MissingCredential)
                )
            }
            credential
        } else {
            null
        }
        val sanitizedUri = sanitizeRemoteUri(uri)

        WorkspacePaths.ensureEmptyDirectory(workspaceDir).getOrElse { _ ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed)
            )
        }

        val effectiveBranch = gitRepository
            .resolveCloneBranch(sanitizedUri, branch, httpsToken)
            .getOrElse { error ->
                cleanupOnFailure(workspaceDir)
                return Result.failure(mapCloneFailure(error, branch))
            }

        gitRepository.cloneFrom(
            remoteUri = sanitizedUri,
            workingDir = workspaceDir,
            branch = effectiveBranch,
            httpsToken = httpsToken
        ).getOrElse { error ->
            cleanupOnFailure(workspaceDir)
            return Result.failure(mapCloneFailure(error, effectiveBranch))
        }

        return buildConfig(
            name,
            sanitizedUri,
            workspaceDir,
            validateBranch = effectiveBranch
        ).also { result ->
            if (result.isFailure) cleanupOnFailure(workspaceDir)
        }
    }

    private fun completeInit(name: String, workspaceDir: File): Result<WorkspaceConfig> {
        WorkspacePaths.ensureEmptyDirectory(workspaceDir).getOrElse { error ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed)
            )
        }

        gitRepository.initOrOpen(workspaceDir).getOrElse { _ ->
            cleanupOnFailure(workspaceDir)
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.InitFailed)
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
        val status = gitRepository.status(workspaceDir).getOrElse { _ ->
            return Result.failure(
                WorkspaceSetupException(WorkspaceSetupError.StorageFailed)
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

    private fun sanitizeRemoteUri(uri: String): String = uri.trim()
}
