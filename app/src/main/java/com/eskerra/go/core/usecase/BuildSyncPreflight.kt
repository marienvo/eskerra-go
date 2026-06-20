package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.git.GitBranchNameValidator
import com.eskerra.go.data.workspace.RemoteUriSecurity
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds a local-only preflight summary before manual sync. */
class BuildSyncPreflight(
    private val remoteSyncRepository: RemoteSyncRepository,
    private val credentialStore: CredentialStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): SyncPreflightSummary =
        withContext(dispatcher) {
            build(config, filesDir)
        }

    private suspend fun build(config: WorkspaceConfig, filesDir: File): SyncPreflightSummary {
        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()
        val workspaceReady = workspaceDir != null &&
            workspaceDir.isDirectory &&
            WorkspacePaths.isValidGitWorkspace(workspaceDir)

        if (!workspaceReady) {
            return blocked(
                SyncError.WorkspaceUnavailable,
                workspaceReady = false,
                userMessage = SyncError.WorkspaceUnavailable.message()
            )
        }

        val remoteUri = config.remoteUri?.trim().orEmpty()
        val remoteConfigured = remoteUri.isNotBlank()
        if (!remoteConfigured) {
            return blocked(
                SyncError.MissingRemoteConfig,
                workspaceReady = true,
                remoteConfigured = false,
                userMessage = SyncError.MissingRemoteConfig.message()
            )
        }

        if (RemoteUriSecurity.containsEmbeddedCredentials(remoteUri)) {
            return blocked(
                SyncError.InvalidRemoteUri,
                workspaceReady = true,
                remoteConfigured = true,
                userMessage = SyncError.InvalidRemoteUri.message()
            )
        }

        if (!RemoteUriSecurity.isSupportedRemoteScheme(remoteUri)) {
            return blocked(
                SyncError.UnsupportedRemoteScheme,
                workspaceReady = true,
                remoteConfigured = true,
                userMessage = SyncError.UnsupportedRemoteScheme.message()
            )
        }

        val branch = config.branch.trim()
        if (branch.isBlank() || GitBranchNameValidator.validate(branch).isFailure) {
            return blocked(
                SyncError.InvalidBranch,
                workspaceReady = true,
                remoteConfigured = true,
                userMessage = SyncError.InvalidBranch.message()
            )
        }

        val credentialPresent = if (remoteUri.startsWith("https://", ignoreCase = true)) {
            val token = credentialStore.readToken(config.relativePath).getOrNull()
            !token.isNullOrBlank()
        } else {
            true
        }

        if (!credentialPresent) {
            return blocked(
                SyncError.MissingCredential,
                workspaceReady = true,
                remoteConfigured = true,
                credentialPresent = false,
                userMessage = SyncError.MissingCredential.message()
            )
        }

        val repoInterventionRequired = remoteSyncRepository.requiresManualIntervention(
            workspaceDir!!
        )
        if (repoInterventionRequired) {
            return blocked(
                SyncError.ManualInterventionRequired,
                workspaceReady = true,
                remoteConfigured = true,
                credentialPresent = true,
                repoInterventionRequired = true,
                userMessage = SyncError.ManualInterventionRequired.message()
            )
        }

        val workspaceStatus = remoteSyncRepository.status(workspaceDir).getOrNull()
        val workingPartition = workspaceStatus?.changedPaths
            ?.let { remoteSyncRepository.partitionChanges(it) }
        val inboxChangeCount = workingPartition?.inboxPaths?.size ?: 0
        val nonInboxChangeCount = workingPartition?.nonInboxPaths?.size ?: 0
        val unsafePathCount = workingPartition?.unsafePaths?.size ?: 0

        val stagedPaths = remoteSyncRepository.readStagedPaths(workspaceDir).getOrNull().orEmpty()
        val stagedPartition = remoteSyncRepository.partitionChanges(stagedPaths)
        val stagedNonInboxCount = stagedPartition.nonInboxPaths.size
        val stagedUnsafeCount = stagedPartition.unsafePaths.size

        val comparison = remoteSyncRepository.compareWithRemote(workspaceDir, branch).getOrNull()
        val aheadCount = comparison?.aheadCount ?: 0
        val behindCount = comparison?.behindCount ?: 0

        // Local changes outside Inbox/ no longer block: they are committed and synced.
        // Only genuinely unsafe paths (.git internals, `..`) stop a sync.
        val blockReason = when {
            unsafePathCount > 0 -> SyncError.UnsafeLocalPath
            stagedUnsafeCount > 0 -> SyncError.UnsafeLocalPath
            else -> null
        }

        val localChangeCount = inboxChangeCount + nonInboxChangeCount
        val userMessage = when {
            blockReason != null -> blockReason.message()
            localChangeCount > 0 -> "Ready to sync $localChangeCount local change(s)."
            behindCount > 0 -> "Ready to sync. Remote has $behindCount commit(s) to integrate."
            aheadCount > 0 -> "Ready to sync. Local branch is $aheadCount commit(s) ahead."
            else -> "Ready to sync."
        }

        return SyncPreflightSummary(
            canSync = blockReason == null,
            blockReason = blockReason,
            workspaceReady = true,
            remoteConfigured = true,
            credentialPresent = true,
            inboxChangeCount = inboxChangeCount,
            nonInboxChangeCount = nonInboxChangeCount,
            unsafePathCount = unsafePathCount,
            stagedNonInboxCount = stagedNonInboxCount,
            stagedUnsafeCount = stagedUnsafeCount,
            aheadCount = aheadCount,
            behindCount = behindCount,
            repoInterventionRequired = false,
            userMessage = userMessage
        )
    }

    private fun blocked(
        reason: SyncError,
        workspaceReady: Boolean = false,
        remoteConfigured: Boolean = false,
        credentialPresent: Boolean = false,
        repoInterventionRequired: Boolean = false,
        userMessage: String
    ) = SyncPreflightSummary(
        canSync = false,
        blockReason = reason,
        workspaceReady = workspaceReady,
        remoteConfigured = remoteConfigured,
        credentialPresent = credentialPresent,
        inboxChangeCount = 0,
        nonInboxChangeCount = 0,
        unsafePathCount = 0,
        stagedNonInboxCount = 0,
        stagedUnsafeCount = 0,
        aheadCount = 0,
        behindCount = 0,
        repoInterventionRequired = repoInterventionRequired,
        userMessage = userMessage
    )
}
