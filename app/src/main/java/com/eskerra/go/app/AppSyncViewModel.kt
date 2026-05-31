package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncRecoveryGuidance
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.feature.sync.SyncUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-scoped sync state for the shell indicator and sync screen. */
class AppSyncViewModel(
    private var config: WorkspaceConfig,
    private val filesDir: File,
    private val loadSyncStatus: LoadSyncStatus,
    private val refreshRemoteSyncStatus: RefreshRemoteSyncStatus,
    private val buildSyncPreflight: BuildSyncPreflight,
    private val buildSafeSyncDiagnostic: BuildSafeSyncDiagnostic,
    private val manualSyncNow: ManualSyncNow,
    private val recordLastSyncAttempt: RecordLastSyncAttempt,
    private val onSyncSuccess: () -> Unit = {},
    private val onConfigUpdated: (WorkspaceConfig) -> Unit = {},
    private val refreshDebounceMs: Long = DEFAULT_REFRESH_DEBOUNCE_MS,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var syncJob: Job? = null
    private var lastRemoteRefreshAtMs: Long = -1L

    fun refreshRemoteStatus(force: Boolean = false) {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        val now = clock()
        if (!force &&
            lastRemoteRefreshAtMs >= 0L &&
            now - lastRemoteRefreshAtMs < refreshDebounceMs
        ) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            emitReadyState(refreshRemoteSyncStatus(config, filesDir))
            lastRemoteRefreshAtMs = clock()
        }
    }

    fun refreshLocalStatus() {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            emitReadyState(loadSyncStatus(config, filesDir))
        }
    }

    fun refreshLocalStatusQuietly() {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            emitReadyState(loadSyncStatus(config, filesDir))
        }
    }

    fun refreshRemoteStatusQuietly(force: Boolean = false) {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        val now = clock()
        if (!force &&
            lastRemoteRefreshAtMs >= 0L &&
            now - lastRemoteRefreshAtMs < refreshDebounceMs
        ) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            emitReadyState(refreshRemoteSyncStatus(config, filesDir))
            lastRemoteRefreshAtMs = clock()
        }
    }

    fun syncNow() {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        loadJob?.cancel()
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
                    recordLastSyncAttempt.recordSuccess(result)
                    onSyncSuccess()
                    lastRemoteRefreshAtMs = clock()
                    val warningMessage = if (result.registryRefreshed) {
                        null
                    } else {
                        SyncError.RegistryRefreshFailed.message()
                    }
                    _uiState.value = SyncUiState.Success(
                        status = result.status,
                        committed = result.committed,
                        pushed = result.pushed,
                        pulled = result.pulled,
                        warningMessage = warningMessage
                    )
                },
                onFailure = { error ->
                    val syncError = when (error) {
                        is SyncException -> error.error
                        else -> SyncError.GitFailed(GENERIC_ERROR_MESSAGE)
                    }
                    recordLastSyncAttempt.recordFailure(syncError)
                    val message = when (error) {
                        is SyncException -> error.error.message()
                        else -> GENERIC_ERROR_MESSAGE
                    }
                    val status = (error as? SyncException)?.let {
                        loadSyncStatus(config, filesDir)
                    }
                    _uiState.value = SyncUiState.Error(
                        status = status,
                        message = message,
                        recoveryAction = SyncRecoveryGuidance.forError(syncError)
                    )
                }
            )
        }
    }

    private suspend fun emitReadyState(status: com.eskerra.go.core.model.SyncStatusSummary) {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }
        val preflight = buildSyncPreflight(config, filesDir)
        val diagnostic = buildSafeSyncDiagnostic(config, filesDir)
        _uiState.value = SyncUiState.Ready(
            status = status,
            remoteUri = config.remoteUri,
            branch = config.branch,
            preflight = preflight,
            diagnostic = diagnostic
        )
    }

    companion object {
        const val GENERIC_ERROR_MESSAGE = "Sync failed. Local notes are still available."
        const val DEFAULT_REFRESH_DEBOUNCE_MS = 30_000L

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadSyncStatus: LoadSyncStatus,
            refreshRemoteSyncStatus: RefreshRemoteSyncStatus,
            buildSyncPreflight: BuildSyncPreflight,
            buildSafeSyncDiagnostic: BuildSafeSyncDiagnostic,
            manualSyncNow: ManualSyncNow,
            recordLastSyncAttempt: RecordLastSyncAttempt,
            onSyncSuccess: () -> Unit = {},
            onConfigUpdated: (WorkspaceConfig) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppSyncViewModel(
                config,
                filesDir,
                loadSyncStatus,
                refreshRemoteSyncStatus,
                buildSyncPreflight,
                buildSafeSyncDiagnostic,
                manualSyncNow,
                recordLastSyncAttempt,
                onSyncSuccess,
                onConfigUpdated
            ) as T
        }
    }
}
