package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncRecoveryGuidance
import com.eskerra.go.core.model.SyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.BuildSafeSyncDiagnostic
import com.eskerra.go.core.usecase.BuildSyncPreflight
import com.eskerra.go.core.usecase.LoadSyncStatus
import com.eskerra.go.core.usecase.ManualSyncNow
import com.eskerra.go.core.usecase.RecordLastSyncAttempt
import com.eskerra.go.core.usecase.RefreshRemoteSyncStatus
import com.eskerra.go.feature.sync.SyncUiState
import com.eskerra.go.feature.sync.holdTrueAtLeast
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val autoSyncRetryDelay: suspend () -> Unit = {
        delay(DEFAULT_AUTO_SYNC_RETRY_DELAY_MS)
    },
    private val syncRunner: suspend (
        WorkspaceConfig,
        File,
        (SyncProgressStep) -> Unit
    ) -> Result<SyncResult> = { syncConfig, syncFilesDir, onProgress ->
        manualSyncNow(syncConfig, syncFilesDir, onProgress)
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    /** True while a sync is running, held true for [SYNC_SPINNER_HOLD_MS] after it ends so a fast
     * sync does not flash the shell's sync spinner on and off. */
    private val _syncSpinnerVisible = MutableStateFlow(false)
    val syncSpinnerVisible: StateFlow<Boolean> = _syncSpinnerVisible.asStateFlow()

    private var loadJob: Job? = null

    // Trigger state is owned exclusively by viewModelScope/Main. Public entry points marshal
    // onto that scope so syncJob and pendingAutoSync never require locks or volatile access.
    private var syncJob: Job? = null
    private var pendingAutoSync = false
    private var lastRemoteRefreshAtMs: Long = -1L

    init {
        viewModelScope.launch {
            uiState.map { it is SyncUiState.Syncing }
                .holdTrueAtLeast(SYNC_SPINNER_HOLD_MS)
                .collect { _syncSpinnerVisible.value = it }
        }
    }

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

    /** Local status first, then optional debounced remote check in one load job. */
    fun refreshShellStatusQuietly(forceRemote: Boolean = false) {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            emitReadyState(loadSyncStatus(config, filesDir))
            if (_uiState.value is SyncUiState.Syncing) {
                return@launch
            }

            val now = clock()
            if (!forceRemote &&
                lastRemoteRefreshAtMs >= 0L &&
                now - lastRemoteRefreshAtMs < refreshDebounceMs
            ) {
                return@launch
            }

            emitReadyState(refreshRemoteSyncStatus(config, filesDir))
            lastRemoteRefreshAtMs = clock()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (syncJob?.isActive == true || _uiState.value is SyncUiState.Syncing) {
                return@launch
            }
            startSync(SyncTrigger.Manual)
        }
    }

    /** Sole entry point for current and future foreground automatic sync triggers. */
    fun requestAutoSync() {
        viewModelScope.launch {
            processAutoSyncRequest()
        }
    }

    private suspend fun processAutoSyncRequest() {
        if (config.remoteUri.isNullOrBlank()) {
            return
        }
        if (syncJob?.isActive == true) {
            pendingAutoSync = true
            return
        }

        val preflight = buildSyncPreflight(config, filesDir)
        if (!preflight.canSync) {
            refreshLocalStatusQuietly()
            return
        }
        if (syncJob?.isActive == true) {
            pendingAutoSync = true
            return
        }
        startSync(SyncTrigger.Automatic)
    }

    private fun startSync(trigger: SyncTrigger) {
        loadJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                var outcome = runSync(trigger)
                while (trigger == SyncTrigger.Automatic &&
                    outcome == SyncRunOutcome.RetryAfterContention
                ) {
                    autoSyncRetryDelay()
                    outcome = runSync(trigger)
                }
            } finally {
                val runFollowUp = pendingAutoSync
                pendingAutoSync = false
                syncJob = null
                if (runFollowUp) {
                    processAutoSyncRequest()
                }
            }
        }
        syncJob = job
        job.start()
    }

    private suspend fun runSync(trigger: SyncTrigger): SyncRunOutcome {
        val currentStatus = when (val state = _uiState.value) {
            is SyncUiState.Ready -> state.status
            is SyncUiState.Error -> state.status ?: loadSyncStatus(config, filesDir)
            is SyncUiState.Success -> state.status
            else -> loadSyncStatus(config, filesDir)
        }

        _uiState.value = SyncUiState.Syncing(
            status = currentStatus,
            step = SyncProgressStep.ValidatingWorkspace
        )

        return syncRunner(config, filesDir) { step ->
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
                SyncRunOutcome.Completed
            },
            onFailure = { error ->
                val syncError = when (error) {
                    is SyncException -> error.error
                    else -> SyncError.GitFailed(GENERIC_ERROR_MESSAGE)
                }
                if (trigger == SyncTrigger.Automatic &&
                    syncError == SyncError.SyncAlreadyRunning
                ) {
                    return@fold SyncRunOutcome.RetryAfterContention
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
                SyncRunOutcome.Completed
            }
        )
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
        const val SYNC_SPINNER_HOLD_MS = 450L
        const val DEFAULT_AUTO_SYNC_RETRY_DELAY_MS = 250L

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

    private enum class SyncTrigger {
        Manual,
        Automatic
    }

    private enum class SyncRunOutcome {
        Completed,
        RetryAfterContention
    }
}
