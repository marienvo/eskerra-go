package com.eskerra.go.core.model

/** Typed sync failures with stable, token-safe user messages. */
sealed interface SyncError {
    fun message(): String

    data object MissingRemoteConfig : SyncError {
        override fun message() = "Remote sync is not configured."
    }

    data object MissingCredential : SyncError {
        override fun message() = "No access token is stored for this workspace."
    }

    data object InvalidRemoteUri : SyncError {
        override fun message() = "Remote URL must not include embedded username or password."
    }

    data object UnsupportedRemoteScheme : SyncError {
        override fun message() = "Only file:// and https:// remotes are supported in this step."
    }

    data object InvalidBranch : SyncError {
        override fun message() = "Enter a valid branch name."
    }

    data object WorkspaceUnavailable : SyncError {
        override fun message() = "Workspace is not available."
    }

    data object NonInboxLocalChanges : SyncError {
        override fun message() =
            "Sync stopped because local changes exist outside Inbox/. Resolve them with Git before syncing."
    }

    data object UnsafeLocalPath : SyncError {
        override fun message() = "Sync stopped because the working tree contains unsafe paths."
    }

    data object AuthenticationFailed : SyncError {
        override fun message() = "Authentication failed. Check the token and try again."
    }

    data object RemoteUnavailable : SyncError {
        override fun message() = "Remote unavailable. Local notes are still available."
    }

    data class RemoteBranchNotFound(val branch: String) : SyncError {
        override fun message() = "Branch \"$branch\" was not found on the remote."
    }

    data object Diverged : SyncError {
        override fun message() =
            "Sync stopped because local and remote histories have diverged. Manual Git repair is required for this PoC."
    }

    data object ConflictRisk : SyncError {
        override fun message() =
            "Sync stopped because local and remote changes conflict. Manual Git repair is required for this PoC."
    }

    data object PushRejected : SyncError {
        override fun message() = "Push was rejected by the remote. Local commits were kept."
    }

    data object ManualInterventionRequired : SyncError {
        override fun message() = "Sync stopped because the repository needs manual Git repair."
    }

    data class GitFailed(val safeMessage: String) : SyncError {
        override fun message() = safeMessage
    }
}

class SyncException(val error: SyncError) : Exception(error.message())
