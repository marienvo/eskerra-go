package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.BinarySyncSummary
import com.eskerra.go.core.model.DownloadedBinary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadDownloadedBinaries
import com.eskerra.go.core.usecase.SyncBinaries
import com.eskerra.go.data.workspace.WorkspacePaths
import com.eskerra.go.feature.sync.BinariesUiState
import com.eskerra.go.feature.sync.DownloadedBinaryRow
import com.eskerra.go.feature.sync.formatByteSize
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the "Downloaded binaries" tile: lists on-device binaries and runs the manual sync. */
class BinariesViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val syncBinaries: SyncBinaries,
    private val loadDownloadedBinaries: LoadDownloadedBinaries
) : ViewModel() {

    private val _uiState = MutableStateFlow(BinariesUiState())
    val uiState: StateFlow<BinariesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun syncNow() {
        val workspaceRoot = workspaceRoot() ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Workspace unavailable.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                statusMessage = null,
                errorMessage = null
            )
            try {
                val summary = syncBinaries(workspaceRoot)
                val binaries = loadDownloadedBinaries(workspaceRoot)
                _uiState.value = binaries.toState().withSummary(summary)
            } catch (cancellation: CancellationException) {
                _uiState.value = _uiState.value.copy(isSyncing = false)
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = error.message ?: "Binaries sync failed."
                )
            }
        }
    }

    private fun refresh() {
        val workspaceRoot = workspaceRoot() ?: return
        viewModelScope.launch {
            _uiState.value = loadDownloadedBinaries(workspaceRoot).toState()
        }
    }

    private fun List<DownloadedBinary>.toState(): BinariesUiState = BinariesUiState(
        items = map { DownloadedBinaryRow(it.relPath, formatByteSize(it.sizeBytes)) },
        totalLabel = formatByteSize(sumOf { it.sizeBytes })
    )

    private fun BinariesUiState.withSummary(summary: BinarySyncSummary): BinariesUiState =
        when (summary) {
            BinarySyncSummary.NotConfigured -> copy(
                isSyncing = false,
                errorMessage = "Configure Cloudflare R2 below to sync binaries."
            )
            is BinarySyncSummary.Completed -> copy(
                isSyncing = false,
                statusMessage = "Downloaded ${summary.downloaded}, removed ${summary.deleted}, " +
                    "skipped ${summary.skipped} (tracked by git)."
            )
            is BinarySyncSummary.Failed -> copy(isSyncing = false, errorMessage = summary.message)
        }

    private fun workspaceRoot(): File? =
        WorkspacePaths.resolve(filesDir, config.relativePath).getOrNull()

    companion object {
        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            syncBinaries: SyncBinaries,
            loadDownloadedBinaries: LoadDownloadedBinaries
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BinariesViewModel(
                config = config,
                filesDir = filesDir,
                syncBinaries = syncBinaries,
                loadDownloadedBinaries = loadDownloadedBinaries
            ) as T
        }
    }
}
