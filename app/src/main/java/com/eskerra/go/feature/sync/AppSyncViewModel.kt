package com.eskerra.go.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.DurableSyncRecord
import com.eskerra.go.core.model.DurableSyncStatus
import com.eskerra.go.core.model.SafeSyncDiagnostic
import com.eskerra.go.core.model.SyncPreflightSummary
import com.eskerra.go.core.model.SyncProgressStep
import com.eskerra.go.core.model.SyncRecoveryAction
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.SyncStateRepository
import com.eskerra.go.core.repository.VaultSyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-scoped sync state for the shell indicator and sync screen. */
class AppSyncViewModel(
    private var config: WorkspaceConfig,
    private val loadSyncStatus: suspend (WorkspaceConfig) -> SyncStatusSummary,
    private val refreshRemoteSyncStatus: suspend (WorkspaceConfig) -> SyncStatusSummary,
    private val buildSyncPreflight: suspend (WorkspaceConfig) -> SyncPreflightSummary,
    private val buildSafeSyncDiagnostic: suspend (WorkspaceConfig) -> SafeSyncDiagnostic,
    private val syncStateRepository: SyncStateRepository,
    private val vaultSyncScheduler: VaultSyncScheduler,
    private val onSyncSuccess: () -> Unit = {},
    private val onConfigUpdated: (WorkspaceConfig) -> Unit = {},
    private val refreshDebounceMs: Long = DEFAULT_REFRESH_DEBOUNCE_MS,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    /** Shell-only spinner intent. */
    private val syncSpinnerRequested = MutableStateFlow(false)

    /** Held true for [SYNC_SPINNER_HOLD_MS] after requested work ends to avoid a shell flash. */
    private val _syncSpinnerVisible = MutableStateFlow(false)
    val syncSpinnerVisible: StateFlow<Boolean> = _syncSpinnerVisible.asStateFlow()

    private var loadJob: Job? = null
    private var lastRemoteRefreshAtMs: Long = -1L
    private var lastStatusSummary: SyncStatusSummary? = null
    private var previousDurableStatus: DurableSyncStatus? = null

    init {
        viewModelScope.launch {
            syncSpinnerRequested
                .holdTrueAtLeast(SYNC_SPINNER_HOLD_MS)
                .collect { _syncSpinnerVisible.value = it }
        }
        viewModelScope.launch {
            syncStateRepository.record.collect { record ->
                handleDurableSyncRecord(record)
            }
        }
    }

    private suspend fun handleDurableSyncRecord(record: DurableSyncRecord) {
        val prevStatus = previousDurableStatus
        previousDurableStatus = record.status

        when (val status = record.status) {
            is DurableSyncStatus.Running -> {
                syncSpinnerRequested.value = true
                val currentSummary = currentOrLoadedSummary()
                val step = SyncProgressStep.entries.find { it.name == status.step }
                    ?: SyncProgressStep.ValidatingWorkspace
                _uiState.value = SyncUiState.Syncing(
                    status = currentSummary,
                    step = step
                )
            }

            is DurableSyncStatus.Pending -> {
                syncSpinnerRequested.value = true
                val currentSummary = currentOrLoadedSummary()
                if (_uiState.value !is SyncUiState.Syncing) {
                    _uiState.value = SyncUiState.Syncing(
                        status = currentSummary,
                        step = SyncProgressStep.ValidatingWorkspace
                    )
                }
            }

            is DurableSyncStatus.Synced -> {
                syncSpinnerRequested.value = false
                val freshSummary = loadSyncStatus(config).also {
                    lastStatusSummary = it
                }
                if (prevStatus is DurableSyncStatus.Running ||
                    prevStatus is DurableSyncStatus.Pending
                ) {
                    onSyncSuccess()
                    _uiState.value = SyncUiState.Success(
                        status = freshSummary,
                        committed = true,
                        pushed = true,
                        pulled = true
                    )
                } else if (_uiState.value is SyncUiState.Loading ||
                    _uiState.value is SyncUiState.Syncing
                ) {
                    emitReadyStateNow(freshSummary)
                }
            }

            is DurableSyncStatus.Retrying -> {
                syncSpinnerRequested.value = false
                val currentSummary = currentOrLoadedSummary()
                _uiState.value = SyncUiState.Error(
                    status = currentSummary,
                    message = status.reason,
                    recoveryAction = SyncRecoveryAction(
                        hint = status.reason,
                        suggestOpenSettings = false
                    )
                )
            }

            is DurableSyncStatus.Blocked -> {
                syncSpinnerRequested.value = false
                val currentSummary = currentOrLoadedSummary()
                _uiState.value = SyncUiState.Error(
                    status = currentSummary,
                    message = status.reason,
                    recoveryAction = SyncRecoveryAction(
                        hint = status.reason,
                        suggestOpenSettings = true
                    )
                )
            }
        }
    }

    private suspend fun currentOrLoadedSummary(): SyncStatusSummary =
        lastStatusSummary ?: loadSyncStatus(config).also { lastStatusSummary = it }

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
            val summary = refreshRemoteSyncStatus(config)
            lastStatusSummary = summary
            emitReadyState(summary)
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
            val summary = loadSyncStatus(config)
            lastStatusSummary = summary
            emitReadyState(summary)
        }
    }

    fun refreshLocalStatusQuietly() {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val summary = loadSyncStatus(config)
            lastStatusSummary = summary
            emitReadyState(summary)
        }
    }

    fun reconcileOnBoot() {
        viewModelScope.launch {
            vaultSyncScheduler.reconcile()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (config.remoteUri.isNullOrBlank()) {
                refreshLocalStatusQuietly()
                return@launch
            }
            syncStateRepository.markGenerationRequested()
            vaultSyncScheduler.scheduleSync()
        }
    }

    /** Sole entry point for current and future foreground automatic sync triggers. */
    fun requestAutoSync() {
        viewModelScope.launch {
            if (config.remoteUri.isNullOrBlank()) {
                refreshLocalStatusQuietly()
                return@launch
            }
            val preflight = buildSyncPreflight(config)
            if (!preflight.canSync) {
                refreshLocalStatusQuietly()
                return@launch
            }
            syncStateRepository.markGenerationRequested()
            vaultSyncScheduler.scheduleSync()
        }
    }

    private suspend fun emitReadyState(status: SyncStatusSummary) {
        if (_uiState.value is SyncUiState.Syncing) {
            return
        }
        emitReadyStateNow(status)
    }

    /** Emits Ready state with fresh preflight and diagnostic summaries. */
    private suspend fun emitReadyStateNow(status: SyncStatusSummary) {
        val preflight = buildSyncPreflight(config)
        val diagnostic = buildSafeSyncDiagnostic(config)
        _uiState.value = SyncUiState.Ready(
            status = status,
            remoteUri = config.remoteUri,
            branch = config.branch,
            preflight = preflight,
            diagnostic = diagnostic
        )
    }

    companion object {
        const val DEFAULT_REFRESH_DEBOUNCE_MS = 30_000L
        const val SYNC_SPINNER_HOLD_MS = 450L

        fun factory(
            config: WorkspaceConfig,
            loadSyncStatus: suspend (WorkspaceConfig) -> SyncStatusSummary,
            refreshRemoteSyncStatus: suspend (WorkspaceConfig) -> SyncStatusSummary,
            buildSyncPreflight: suspend (WorkspaceConfig) -> SyncPreflightSummary,
            buildSafeSyncDiagnostic: suspend (WorkspaceConfig) -> SafeSyncDiagnostic,
            syncStateRepository: SyncStateRepository,
            vaultSyncScheduler: VaultSyncScheduler,
            onSyncSuccess: () -> Unit = {},
            onConfigUpdated: (WorkspaceConfig) -> Unit = {}
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppSyncViewModel(
                config = config,
                loadSyncStatus = loadSyncStatus,
                refreshRemoteSyncStatus = refreshRemoteSyncStatus,
                buildSyncPreflight = buildSyncPreflight,
                buildSafeSyncDiagnostic = buildSafeSyncDiagnostic,
                syncStateRepository = syncStateRepository,
                vaultSyncScheduler = vaultSyncScheduler,
                onSyncSuccess = onSyncSuccess,
                onConfigUpdated = onConfigUpdated
            ) as T
        }
    }
}
