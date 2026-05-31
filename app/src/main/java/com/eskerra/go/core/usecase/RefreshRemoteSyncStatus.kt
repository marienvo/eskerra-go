package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.credentials.CredentialStore
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fetches remote refs, then loads sync status from updated local Git state. */
class RefreshRemoteSyncStatus(
    private val remoteSyncRepository: RemoteSyncRepository,
    private val credentialStore: CredentialStore,
    private val loadSyncStatus: LoadSyncStatus,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): SyncStatusSummary =
        withContext(dispatcher) {
            val localStatus = loadSyncStatus(config, filesDir)
            if (localStatus.state == SyncStatusState.Unavailable) {
                return@withContext localStatus
            }

            val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()
                ?: return@withContext SyncStatusSummary.error

            val remoteUri = config.remoteUri?.trim().orEmpty()
            val httpsToken = if (remoteUri.startsWith("https://", ignoreCase = true)) {
                credentialStore.readToken(config.relativePath).getOrNull()
            } else {
                null
            }

            remoteSyncRepository.fetch(workspaceDir, httpsToken).fold(
                onSuccess = { loadSyncStatus(config, filesDir) },
                onFailure = {
                    SyncStatusSummary(
                        state = SyncStatusState.Error,
                        branch = config.branch.takeIf { it.isNotBlank() },
                        changedCount = 0,
                        aheadCount = 0,
                        behindCount = 0,
                        message = FETCH_FAILED_MESSAGE
                    )
                }
            )
        }

    companion object {
        const val FETCH_FAILED_MESSAGE = "Could not check remote sync status."
    }
}
