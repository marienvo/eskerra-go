package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.RemoteSyncSettingsException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.ClearRemoteSyncSettings
import com.eskerra.go.core.usecase.LoadRemoteSyncSettings
import com.eskerra.go.core.usecase.SaveRemoteSyncSettings
import com.eskerra.go.core.usecase.TestRemoteConnection
import com.eskerra.go.feature.sync.RemoteSyncSettingsUiState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncSettingsViewModel(
    private var config: WorkspaceConfig,
    private val filesDir: File,
    private val loadRemoteSyncSettings: LoadRemoteSyncSettings,
    private val saveRemoteSyncSettings: SaveRemoteSyncSettings,
    private val clearRemoteSyncSettings: ClearRemoteSyncSettings,
    private val testRemoteConnection: TestRemoteConnection,
    private val onConfigUpdated: (WorkspaceConfig) -> Unit
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<RemoteSyncSettingsUiState>(RemoteSyncSettingsUiState.Loading)
    val uiState: StateFlow<RemoteSyncSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onRemoteUriChange(value: String) {
        updateReady { it.copy(editRemoteUri = value, errorMessage = null) }
    }

    fun onBranchChange(value: String) {
        updateReady { it.copy(editBranch = value, errorMessage = null) }
    }

    fun onReplacementTokenChange(value: String) {
        updateReady { it.copy(replacementToken = value, errorMessage = null) }
    }

    fun saveSettings() {
        val ready = currentReady() ?: return
        viewModelScope.launch {
            _uiState.value = ready.copy(isSaving = true, statusMessage = null, errorMessage = null)
            val token = ready.replacementToken.trim().ifBlank { null }
            saveRemoteSyncSettings(
                config = config,
                remoteUri = ready.editRemoteUri,
                branch = ready.editBranch,
                replacementToken = token,
                filesDir = filesDir
            ).fold(
                onSuccess = { updated ->
                    applyConfig(updated)
                    _uiState.value = buildReadyFromSettings(
                        loadRemoteSyncSettings(config),
                        editRemoteUri = ready.editRemoteUri,
                        editBranch = ready.editBranch,
                        replacementToken = "",
                        statusMessage = "Remote sync settings saved."
                    )
                },
                onFailure = { error ->
                    _uiState.value = ready.copy(
                        isSaving = false,
                        errorMessage = settingsMessage(error)
                    )
                }
            )
        }
    }

    fun testConnection() {
        val ready = currentReady() ?: return
        viewModelScope.launch {
            _uiState.value = ready.copy(isTesting = true, statusMessage = null, errorMessage = null)
            val token = ready.replacementToken.trim().ifBlank { null }
            testRemoteConnection(
                config = config,
                filesDir = filesDir,
                remoteUri = ready.editRemoteUri,
                branch = ready.editBranch,
                replacementToken = token
            ).fold(
                onSuccess = {
                    _uiState.value = ready.copy(
                        isTesting = false,
                        statusMessage = "Connection successful."
                    )
                },
                onFailure = { error ->
                    _uiState.value = ready.copy(
                        isTesting = false,
                        errorMessage = settingsMessage(error)
                    )
                }
            )
        }
    }

    fun clearSettings() {
        val ready = currentReady() ?: return
        viewModelScope.launch {
            _uiState.value =
                ready.copy(isClearing = true, statusMessage = null, errorMessage = null)
            clearRemoteSyncSettings(config, filesDir).fold(
                onSuccess = { updated ->
                    applyConfig(updated)
                    _uiState.value = buildReadyFromSettings(
                        loadRemoteSyncSettings(config),
                        statusMessage = "Remote sync settings cleared. Local notes are unchanged."
                    )
                },
                onFailure = { error ->
                    _uiState.value = ready.copy(
                        isClearing = false,
                        errorMessage = settingsMessage(error)
                    )
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RemoteSyncSettingsUiState.Loading
            val settings = loadRemoteSyncSettings(config)
            _uiState.value = buildReadyFromSettings(settings)
        }
    }

    private fun applyConfig(updated: WorkspaceConfig) {
        config = updated
        onConfigUpdated(updated)
    }

    private fun buildReadyFromSettings(
        settings: com.eskerra.go.core.model.RemoteSyncSettings,
        editRemoteUri: String = settings.remoteUri.orEmpty(),
        editBranch: String = settings.branch,
        replacementToken: String = "",
        statusMessage: String? = null
    ): RemoteSyncSettingsUiState.Ready = RemoteSyncSettingsUiState.Ready(
        displayedRemoteUri = settings.remoteUri,
        displayedBranch = settings.branch,
        hasStoredCredential = settings.hasStoredCredential,
        isConfigured = settings.isConfigured,
        editRemoteUri = editRemoteUri,
        editBranch = editBranch,
        replacementToken = replacementToken,
        statusMessage = statusMessage
    )

    private fun currentReady(): RemoteSyncSettingsUiState.Ready? =
        _uiState.value as? RemoteSyncSettingsUiState.Ready

    private fun updateReady(
        transform: (RemoteSyncSettingsUiState.Ready) -> RemoteSyncSettingsUiState.Ready
    ) {
        val ready = currentReady() ?: return
        _uiState.value = transform(ready)
    }

    private fun settingsMessage(error: Throwable): String = when (error) {
        is RemoteSyncSettingsException -> error.error.message()
        else -> "Remote sync settings failed."
    }

    companion object {
        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadRemoteSyncSettings: LoadRemoteSyncSettings,
            saveRemoteSyncSettings: SaveRemoteSyncSettings,
            clearRemoteSyncSettings: ClearRemoteSyncSettings,
            testRemoteConnection: TestRemoteConnection,
            onConfigUpdated: (WorkspaceConfig) -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncSettingsViewModel(
                config = config,
                filesDir = filesDir,
                loadRemoteSyncSettings = loadRemoteSyncSettings,
                saveRemoteSyncSettings = saveRemoteSyncSettings,
                clearRemoteSyncSettings = clearRemoteSyncSettings,
                testRemoteConnection = testRemoteConnection,
                onConfigUpdated = onConfigUpdated
            ) as T
        }
    }
}
