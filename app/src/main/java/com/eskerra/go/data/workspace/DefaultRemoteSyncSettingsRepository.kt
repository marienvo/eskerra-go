package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.RemoteSyncSettings
import com.eskerra.go.core.model.RemoteSyncSettingsError
import com.eskerra.go.core.model.RemoteSyncSettingsException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.core.repository.RemoteSyncSettingsRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitBranchNameValidator
import com.eskerra.go.data.git.SyncGitErrorMapper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultRemoteSyncSettingsRepository(
    private val workspaceStore: WorkspaceStore,
    private val credentialStore: CredentialStore,
    private val remoteSyncRepository: RemoteSyncRepository
) : RemoteSyncSettingsRepository {

    override suspend fun readSettings(config: WorkspaceConfig): RemoteSyncSettings {
        val remoteUri = config.remoteUri?.trim()?.ifBlank { null }
        val hasToken = credentialStore.readToken(config.relativePath)
            .getOrNull()
            ?.isNullOrBlank() == false
        return RemoteSyncSettings(
            remoteUri = remoteUri,
            branch = config.branch,
            isConfigured = remoteUri != null,
            hasStoredCredential = hasToken
        )
    }

    override suspend fun saveSettings(
        config: WorkspaceConfig,
        remoteUri: String,
        branch: String,
        replacementToken: String?,
        filesDir: File
    ): Result<WorkspaceConfig> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(filesDir, config.relativePath).getOrElse {
            return@withContext Result.failure(it)
        }

        val sanitizedUri = remoteUri.trim()
        if (sanitizedUri.isBlank()) {
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.MissingRemoteUri)
            )
        }
        if (RemoteUriSecurity.containsEmbeddedCredentials(sanitizedUri)) {
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.InvalidRemoteUri)
            )
        }
        if (!RemoteUriSecurity.isSupportedRemoteScheme(sanitizedUri)) {
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.UnsupportedRemoteScheme)
            )
        }

        val trimmedBranch = branch.trim()
        if (GitBranchNameValidator.validate(trimmedBranch).isFailure) {
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.InvalidBranch)
            )
        }

        val httpsToken = resolveHttpsToken(
            relativePath = config.relativePath,
            remoteUri = sanitizedUri,
            previousRemoteUri = config.remoteUri,
            replacementToken = replacementToken?.trim().orEmpty()
        ).getOrElse { return@withContext Result.failure(it) }

        val previousOriginUrl = remoteSyncRepository.readOriginUrl(workspaceDir).getOrNull()
        val previousToken = credentialStore.readToken(config.relativePath).getOrNull()
        val replacement = replacementToken?.trim().orEmpty()
        val shouldPersistToken = httpsToken != null && replacement.isNotEmpty()

        remoteSyncRepository
            .configureSanitizedOrigin(workspaceDir, sanitizedUri)
            .getOrElse { error ->
                return@withContext Result.failure(mapGitFailure(error, trimmedBranch))
            }

        val effectiveBranch = remoteSyncRepository
            .ensureLocalBranch(workspaceDir, trimmedBranch, httpsToken)
            .getOrElse { error ->
                restoreOrigin(workspaceDir, previousOriginUrl)
                return@withContext Result.failure(mapGitFailure(error, trimmedBranch))
            }

        val updated = config.copy(
            remoteUri = sanitizedUri,
            branch = effectiveBranch
        )
        try {
            workspaceStore.save(updated)
        } catch (_: Exception) {
            restoreOrigin(workspaceDir, previousOriginUrl)
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.MetadataSaveFailed)
            )
        }

        if (shouldPersistToken) {
            credentialStore.saveToken(config.relativePath, httpsToken).getOrElse {
                rollbackSavedSettings(config, workspaceDir, previousOriginUrl, previousToken)
                return@withContext Result.failure(
                    RemoteSyncSettingsException(RemoteSyncSettingsError.CredentialSaveFailed)
                )
            }
        }

        Result.success(updated)
    }

    override suspend fun replaceToken(relativePath: String, token: String): Result<Unit> {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.MissingCredential)
            )
        }
        return credentialStore.saveToken(relativePath, trimmed)
    }

    override suspend fun clearSettings(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<WorkspaceConfig> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(filesDir, config.relativePath).getOrElse {
            return@withContext Result.failure(it)
        }
        val cleared = config.copy(remoteUri = null)
        val previousOriginUrl = remoteSyncRepository.readOriginUrl(workspaceDir).getOrNull()
        val previousToken = credentialStore.readToken(config.relativePath).getOrNull()

        remoteSyncRepository.clearSanitizedOrigin(workspaceDir).getOrElse {
            return@withContext Result.failure(
                RemoteSyncSettingsException(
                    RemoteSyncSettingsError.GitFailed("Could not clear remote sync settings.")
                )
            )
        }
        credentialStore.clear(config.relativePath).getOrElse {
            rollbackSavedSettings(config, workspaceDir, previousOriginUrl, previousToken)
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.CredentialSaveFailed)
            )
        }
        try {
            workspaceStore.save(cleared)
        } catch (_: Exception) {
            rollbackSavedSettings(config, workspaceDir, previousOriginUrl, previousToken)
            return@withContext Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.MetadataSaveFailed)
            )
        }
        Result.success(cleared)
    }

    override suspend fun testConnection(
        config: WorkspaceConfig,
        filesDir: File,
        remoteUri: String?,
        branch: String?,
        replacementToken: String?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val sanitizedUri = (remoteUri ?: config.remoteUri)?.trim().orEmpty()
            if (sanitizedUri.isBlank()) {
                return@withContext Result.failure(
                    RemoteSyncSettingsException(RemoteSyncSettingsError.MissingRemoteUri)
                )
            }
            if (RemoteUriSecurity.containsEmbeddedCredentials(sanitizedUri)) {
                return@withContext Result.failure(
                    RemoteSyncSettingsException(RemoteSyncSettingsError.InvalidRemoteUri)
                )
            }
            if (!RemoteUriSecurity.isSupportedRemoteScheme(sanitizedUri)) {
                return@withContext Result.failure(
                    RemoteSyncSettingsException(RemoteSyncSettingsError.UnsupportedRemoteScheme)
                )
            }

            val trimmedBranch = (branch ?: config.branch).trim()
            if (GitBranchNameValidator.validate(trimmedBranch).isFailure) {
                return@withContext Result.failure(
                    RemoteSyncSettingsException(RemoteSyncSettingsError.InvalidBranch)
                )
            }

            val httpsToken = resolveHttpsToken(
                relativePath = config.relativePath,
                remoteUri = sanitizedUri,
                previousRemoteUri = config.remoteUri,
                replacementToken = replacementToken
            ).getOrElse { return@withContext Result.failure(it) }

            remoteSyncRepository
                .probeRemoteConnection(sanitizedUri, trimmedBranch, httpsToken)
                .getOrElse { error ->
                    return@withContext Result.failure(mapGitFailure(error, trimmedBranch))
                }
            Result.success(Unit)
        }
    }

    private suspend fun resolveHttpsToken(
        relativePath: String,
        remoteUri: String,
        previousRemoteUri: String? = null,
        replacementToken: String? = null
    ): Result<String?> {
        if (!remoteUri.startsWith("https://", ignoreCase = true)) {
            return Result.success(null)
        }
        val trimmedReplacement = replacementToken?.trim().orEmpty()
        if (trimmedReplacement.isNotEmpty()) {
            return Result.success(trimmedReplacement)
        }
        val previousTrimmed = previousRemoteUri?.trim().orEmpty()
        if (previousTrimmed.isNotEmpty() && previousTrimmed != remoteUri.trim()) {
            return Result.failure(
                RemoteSyncSettingsException(
                    RemoteSyncSettingsError.RemoteUrlChangedRequiresCredential
                )
            )
        }
        val stored = credentialStore.readToken(relativePath).getOrElse {
            return Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.CredentialSaveFailed)
            )
        }
        if (!stored.isNullOrBlank()) {
            return Result.success(stored)
        }
        return Result.failure(
            RemoteSyncSettingsException(RemoteSyncSettingsError.MissingCredential)
        )
    }

    private fun resolveWorkspace(filesDir: File, relativePath: String): Result<File> {
        val workspaceDir = WorkspacePaths.resolve(filesDir, relativePath).getOrElse {
            return Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.WorkspaceUnavailable)
            )
        }
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return Result.failure(
                RemoteSyncSettingsException(RemoteSyncSettingsError.WorkspaceUnavailable)
            )
        }
        return Result.success(workspaceDir)
    }

    private suspend fun rollbackSavedSettings(
        previousConfig: WorkspaceConfig,
        workspaceDir: File,
        previousOriginUrl: String?,
        previousToken: String?
    ) {
        try {
            workspaceStore.save(previousConfig)
        } catch (_: Exception) {
            // Best-effort rollback; caller still reports credential save failure.
        }
        restoreOrigin(workspaceDir, previousOriginUrl)
        restoreToken(previousConfig.relativePath, previousToken)
    }

    private fun restoreOrigin(workspaceDir: File, previousOriginUrl: String?) {
        if (previousOriginUrl != null) {
            remoteSyncRepository.configureSanitizedOrigin(workspaceDir, previousOriginUrl)
        } else {
            remoteSyncRepository.clearSanitizedOrigin(workspaceDir)
        }
    }

    private suspend fun restoreToken(relativePath: String, previousToken: String?) {
        if (previousToken != null) {
            credentialStore.saveToken(relativePath, previousToken)
        } else {
            credentialStore.clear(relativePath)
        }
    }

    private fun mapGitFailure(error: Throwable, branch: String): RemoteSyncSettingsException {
        val syncException = SyncGitErrorMapper.mapFailure(error, branch)
        return RemoteSyncSettingsException(
            RemoteSyncSettingsError.fromSyncError(syncException.error)
        )
    }
}
