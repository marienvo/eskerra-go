package com.eskerra.go.data.workspace

/** User-facing setup failures with stable messages. */
sealed class WorkspaceSetupError {
    abstract fun message(): String

    data object BlankName : WorkspaceSetupError() {
        override fun message() = "Enter a workspace name."
    }

    data object BlankRemoteUri : WorkspaceSetupError() {
        override fun message() = "Enter a remote URI to clone from."
    }

    data object BlankBranch : WorkspaceSetupError() {
        override fun message() = "Enter a branch name."
    }

    data object UnsupportedRemoteScheme : WorkspaceSetupError() {
        override fun message() =
            "Only file:// remotes are supported in this step. HTTPS auth is not wired yet."
    }

    data object CredentialBearingRemoteUri : WorkspaceSetupError() {
        override fun message() = "Remote URL must not include embedded username or password."
    }

    data class BranchNotFound(val branch: String) : WorkspaceSetupError() {
        override fun message() = "Branch \"$branch\" was not found on the remote."
    }

    data class InvalidRepository(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Repository not found or invalid."
    }

    data class AuthenticationFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Authentication failed."
    }

    data class CloneFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Clone failed."
    }

    data class InitFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Initialize failed."
    }

    data class StorageFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Storage setup failed."
    }

    data class MetadataSaveFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Could not save workspace settings."
    }

    data class CredentialSaveFailed(val detail: String?) : WorkspaceSetupError() {
        override fun message() = "Could not save credentials."
    }
}

class WorkspaceSetupException(val error: WorkspaceSetupError) : Exception(error.message())

fun mapCloneFailure(error: Throwable, branch: String): WorkspaceSetupException {
    val text = error.message.orEmpty()
    return WorkspaceSetupException(
        when {
            isBranchRefError(text, branch) -> WorkspaceSetupError.BranchNotFound(branch)
            isAuthenticationError(text) -> WorkspaceSetupError.AuthenticationFailed(error.message)
            isInvalidRepositoryError(text) -> WorkspaceSetupError.InvalidRepository(error.message)
            else -> WorkspaceSetupError.CloneFailed(error.message)
        }
    )
}

internal fun isBranchRefError(message: String, branch: String): Boolean {
    if (message.contains("unknown branch", ignoreCase = true)) return true
    if (message.contains("bad ref", ignoreCase = true)) return true
    if (message.contains("cannot resolve", ignoreCase = true) &&
        message.contains("ref", ignoreCase = true)
    ) {
        return true
    }
    if (message.contains("cannot be resolved", ignoreCase = true) &&
        message.contains("ref", ignoreCase = true)
    ) {
        return true
    }
    if (message.contains("Ref \"$branch\"", ignoreCase = true)) return true
    if (message.contains("ref: $branch", ignoreCase = true)) return true
    if (message.contains("refs/heads/$branch", ignoreCase = true)) return true
    return message.contains("Remote branch", ignoreCase = true) &&
        message.contains(branch, ignoreCase = true)
}

internal fun isInvalidRepositoryError(message: String): Boolean {
    if (message.contains("not a git repository", ignoreCase = true)) return true
    if (message.contains("No such file", ignoreCase = true)) return true
    if (message.contains("cannot access", ignoreCase = true)) return true
    if (message.contains("Remote repository", ignoreCase = true)) return true
    if (message.contains("Invalid remote", ignoreCase = true)) return true
    if (message.contains("repository", ignoreCase = true) &&
        message.contains("not found", ignoreCase = true)
    ) {
        return true
    }
    if (message.contains("does not exist", ignoreCase = true) &&
        !message.contains("Ref", ignoreCase = true)
    ) {
        return true
    }
    if (message.contains("URI", ignoreCase = true) &&
        message.contains("not found", ignoreCase = true)
    ) {
        return true
    }
    return false
}

internal fun isAuthenticationError(message: String): Boolean =
    message.contains("authentication", ignoreCase = true) ||
        message.contains("not authorized", ignoreCase = true) ||
        message.contains("401", ignoreCase = false) ||
        message.contains("403", ignoreCase = false) ||
        message.contains("credentials", ignoreCase = true)
