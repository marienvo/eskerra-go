package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.feature.sync.SyncUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncViewModel(
    private var config: WorkspaceConfig,
    private val filesDir: File,
    private val loadSyncStatus: LoadSyncStatus,
    private val manualSyncNow: ManualSyncNow,
    private val onSyncSuccess: () -> Unit = {},
    private val onConfigUpdated: (WorkspaceConfig) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var syncJob: Job? = null

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            val status = loadSyncStatus(config, filesDir)
            _uiState.value = SyncUiState.Ready(
                status = status,
                remoteUri = config.remoteUri,
                branch = config.branch
            )
        }
    }

    fun syncNow() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val currentStatus = when (val state = _uiState.value) {
                is SyncUiState.Ready -> state.status
                is SyncUiState.Error -> state.status ?: loadSyncStatus(config, filesDir)
                is SyncUiState.Success -> state.status
                else -> loadSyncStatus(config, filesDir)
            }

            _uiState.value = SyncUiState.Syncing(
                status = currentStatus,
                step = com.eskerra.go.core.model.SyncProgressStep.ValidatingWorkspace
            )

            manualSyncNow(config, filesDir) { step ->
                _uiState.value = SyncUiState.Syncing(
                    status = currentStatus,
                    step = step
                )
            }.fold(
                onSuccess = { result ->
                    result.updatedConfig?.let { updated ->
                        config = updated
                        onConfigUpdated(updated)
                    }
                    onSyncSuccess()
                    _uiState.value = SyncUiState.Success(
                        status = result.status,
                        committed = result.committed,
                        pushed = result.pushed,
                        pulled = result.pulled
                    )
                },
                onFailure = { error ->
                    val message = when (error) {
                        is SyncException -> error.error.message()
                        else -> GENERIC_ERROR_MESSAGE
                    }
                    val status = (error as? SyncException)?.let {
                        loadSyncStatus(config, filesDir)
                    }
                    _uiState.value = SyncUiState.Error(
                        status = status,
                        message = message
                    )
                }
            )
        }
    }

    companion object {
        const val GENERIC_ERROR_MESSAGE = "Sync failed. Local notes are still available."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadSyncStatus: LoadSyncStatus,
            manualSyncNow: ManualSyncNow,
            onSyncSuccess: () -> Unit = {},
            onConfigUpdated: (WorkspaceConfig) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncViewModel(
                config,
                filesDir,
                loadSyncStatus,
                manualSyncNow,
                onSyncSuccess,
                onConfigUpdated
            ) as T
        }
    }
}
