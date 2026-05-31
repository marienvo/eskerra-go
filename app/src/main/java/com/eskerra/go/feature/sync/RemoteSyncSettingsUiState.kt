package com.eskerra.go.feature.sync

/** UI state for the remote sync settings screen. Never exposes a stored token. */
sealed interface RemoteSyncSettingsUiState {
    data object Loading : RemoteSyncSettingsUiState

    data class Ready(
        val displayedRemoteUri: String?,
        val displayedBranch: String,
        val hasStoredCredential: Boolean,
        val isConfigured: Boolean,
        val editRemoteUri: String,
        val editBranch: String,
        val replacementToken: String,
        val isSaving: Boolean = false,
        val isTesting: Boolean = false,
        val isClearing: Boolean = false,
        val statusMessage: String? = null,
        val errorMessage: String? = null
    ) : RemoteSyncSettingsUiState
}
