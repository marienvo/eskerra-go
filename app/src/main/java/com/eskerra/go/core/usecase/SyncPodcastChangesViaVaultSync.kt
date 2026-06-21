package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/**
 * Adapts the unconditional [ManualSyncNow] engine to the [PodcastChangeSync] shape so
 * the podcast refresh commits, merges (with conflict copies), and pushes *every* pending
 * vault change through the always-sync route, instead of the podcast-only commit channel.
 *
 * The podcast channel's best-effort contract is preserved for deferrable failures: when
 * another sync already holds the lock, the remote is temporarily unreachable, or a push
 * is rejected by a racing remote, the change is (or will be) committed locally, so the
 * result is reported as a pending push rather than surfaced as a hard error. Genuine
 * configuration, credential, or workspace problems still propagate as failures.
 *
 * [runVaultSync] is a seam over [ManualSyncNow.invoke] to keep this adapter unit-testable.
 */
class SyncPodcastChangesViaVaultSync(
    private val runVaultSync: suspend (WorkspaceConfig, File) -> Result<SyncResult>
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<PodcastSyncResult> = runVaultSync(config, filesDir).fold(
        onSuccess = { result ->
            Result.success(
                PodcastSyncResult(
                    committed = result.committed,
                    commitId = result.commitId,
                    pushed = result.pushed,
                    pendingPush = result.committed && !result.pushed
                )
            )
        },
        onFailure = { error ->
            if (isDeferrable((error as? SyncException)?.error)) {
                // The change stays on disk; another in-flight sync or the next attempt
                // commits and pushes it. Never block the refresh on it.
                Result.success(
                    PodcastSyncResult(
                        committed = false,
                        commitId = null,
                        pushed = false,
                        pendingPush = true
                    )
                )
            } else {
                Result.failure(error)
            }
        }
    )

    private fun isDeferrable(error: SyncError?): Boolean = when (error) {
        SyncError.SyncAlreadyRunning,
        SyncError.RemoteUnavailable,
        SyncError.PushRejected -> true
        else -> false
    }
}
