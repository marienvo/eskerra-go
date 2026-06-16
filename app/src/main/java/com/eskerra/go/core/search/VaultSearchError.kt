package com.eskerra.go.core.search

/** Typed, user-safe failures for vault search and index maintenance. */
sealed interface VaultSearchError {
    fun message(): String

    fun canRetry(): Boolean = false

    data object WorkspaceUnavailable : VaultSearchError {
        override fun message() = "Workspace is not available."
    }

    data object IndexOpenFailed : VaultSearchError {
        override fun message() = "Search index could not be opened."
        override fun canRetry() = true
    }

    data object IndexCorrupt : VaultSearchError {
        override fun message() = "Search index is corrupt. Retry indexing."
        override fun canRetry() = true
    }

    data object Fts5Unsupported : VaultSearchError {
        override fun message() = "Full-text search is not supported on this device."
    }

    data object QueryFailed : VaultSearchError {
        override fun message() = "Search query failed. Try a simpler term."
        override fun canRetry() = true
    }

    data class IndexBuildFailed(val safeMessage: String) : VaultSearchError {
        override fun message() = safeMessage
        override fun canRetry() = true
    }

    data class Unknown(val safeMessage: String) : VaultSearchError {
        override fun message() = safeMessage
        override fun canRetry() = true
    }
}

class VaultSearchException(val error: VaultSearchError) : Exception(error.message())
