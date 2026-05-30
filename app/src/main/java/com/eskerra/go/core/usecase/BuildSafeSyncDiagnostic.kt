package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.LastSyncStatusStore
import com.eskerra.go.data.workspace.RemoteUriDisplay
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds non-secret sync diagnostics for the UI. */
class BuildSafeSyncDiagnostic(
    private val buildSyncPreflight: BuildSyncPreflight,
    private val lastSyncStatusStore: LastSyncStatusStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): SafeSyncDiagnostic =
        withContext(dispatcher) {
            val preflight = buildSyncPreflight(config, filesDir)
            val lastSync = lastSyncStatusStore.readLastSyncStatus()
            SafeSyncDiagnostic(
                branch = config.branch.takeIf { it.isNotBlank() },
                sanitizedRemote = RemoteUriDisplay.sanitize(config.remoteUri),
                inboxChangeCount = preflight.inboxChangeCount,
                nonInboxChangeCount = preflight.nonInboxChangeCount,
                aheadCount = preflight.aheadCount,
                behindCount = preflight.behindCount,
                lastSync = lastSync
            )
        }
}
