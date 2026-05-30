package com.eskerra.go.data.git

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.data.workspace.isAuthenticationError
import com.eskerra.go.data.workspace.isRemoteUnavailableError

/** Maps low-level Git failures to typed, token-safe [SyncError] values. */
object SyncGitErrorMapper {

    fun mapFailure(error: Throwable, branch: String = ""): SyncException {
        val text = error.message.orEmpty()
        val syncError: SyncError = when {
            isAuthenticationError(text) -> SyncError.AuthenticationFailed
            isRemoteUnavailableError(text) -> SyncError.RemoteUnavailable
            isPushRejected(text) -> SyncError.PushRejected
            isDiverged(text) -> SyncError.Diverged
            isConflictRisk(text) -> SyncError.ConflictRisk
            isManualIntervention(text) -> SyncError.ManualInterventionRequired
            else -> SyncError.GitFailed("Sync failed.")
        }
        return SyncException(syncError)
    }

    private fun isPushRejected(message: String): Boolean =
        message.contains("push rejected", ignoreCase = true) ||
            message.contains("non-fast-forward", ignoreCase = true) ||
            message.contains("rejected", ignoreCase = true) &&
            message.contains("remote", ignoreCase = true)

    private fun isDiverged(message: String): Boolean =
        message.contains("diverged", ignoreCase = true) ||
            message.contains("not a fast-forward", ignoreCase = true) ||
            message.contains("not fast-forwardable", ignoreCase = true)

    private fun isConflictRisk(message: String): Boolean =
        message.contains("conflict", ignoreCase = true) ||
            message.contains("merge", ignoreCase = true) &&
            message.contains("failed", ignoreCase = true)

    private fun isManualIntervention(message: String): Boolean =
        message.contains("manual", ignoreCase = true) ||
            message.contains("unmerged", ignoreCase = true) ||
            message.contains("MERGING", ignoreCase = true)
}
