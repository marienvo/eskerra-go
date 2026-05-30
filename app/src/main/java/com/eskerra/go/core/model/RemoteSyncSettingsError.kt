package com.eskerra.go.core.model

/** Typed, token-safe failures for remote sync settings flows. */
sealed interface RemoteSyncSettingsError {
    fun message(): String

    data object MissingRemoteUri : RemoteSyncSettingsError {
        override fun message() = "Enter a remote URL."
    }

    data object MissingCredential : RemoteSyncSettingsError {
        override fun message() = "Enter an access token for HTTPS remotes."
    }

    data object RemoteUrlChangedRequiresCredential : RemoteSyncSettingsError {
        override fun message() = "Enter a new access token when changing the remote URL."
    }

    data object InvalidRemoteUri : RemoteSyncSettingsError {
        override fun message() = "Remote URL must not include embedded username or password."
    }

    data object UnsupportedRemoteScheme : RemoteSyncSettingsError {
        override fun message() = "Only file:// and https:// remotes are supported."
    }

    data object InvalidBranch : RemoteSyncSettingsError {
        override fun message() = "Enter a valid branch name."
    }

    data object WorkspaceUnavailable : RemoteSyncSettingsError {
        override fun message() = "Workspace is not available."
    }

    data object AuthenticationFailed : RemoteSyncSettingsError {
        override fun message() = "Authentication failed. Check the token and try again."
    }

    data object RemoteUnavailable : RemoteSyncSettingsError {
        override fun message() = "Remote unavailable. Check the URL and network."
    }

    data class RemoteBranchNotFound(val branch: String) : RemoteSyncSettingsError {
        override fun message() = "Branch \"$branch\" was not found on the remote."
    }

    data class LocalBranchNotFound(val branch: String) : RemoteSyncSettingsError {
        override fun message() =
            "Branch \"$branch\" is not available locally. Check the branch name and remote."
    }

    data object MetadataSaveFailed : RemoteSyncSettingsError {
        override fun message() = "Could not save remote sync settings."
    }

    data object CredentialSaveFailed : RemoteSyncSettingsError {
        override fun message() = "Could not save the access token."
    }

    data class GitFailed(val safeMessage: String) : RemoteSyncSettingsError {
        override fun message() = safeMessage
    }

    companion object {
        fun fromSyncError(error: SyncError): RemoteSyncSettingsError = when (error) {
            SyncError.MissingRemoteConfig -> MissingRemoteUri
            SyncError.MissingCredential -> MissingCredential
            SyncError.InvalidRemoteUri -> InvalidRemoteUri
            SyncError.UnsupportedRemoteScheme -> UnsupportedRemoteScheme
            SyncError.InvalidBranch -> InvalidBranch
            SyncError.WorkspaceUnavailable -> WorkspaceUnavailable
            SyncError.AuthenticationFailed -> AuthenticationFailed
            SyncError.RemoteUnavailable -> RemoteUnavailable
            is SyncError.RemoteBranchNotFound -> RemoteBranchNotFound(error.branch)
            is SyncError.LocalBranchNotFound -> LocalBranchNotFound(error.branch)
            is SyncError.GitFailed -> GitFailed(error.safeMessage)
            else -> GitFailed("Remote sync settings failed.")
        }
    }
}

class RemoteSyncSettingsException(val error: RemoteSyncSettingsError) :
    Exception(error.message())
