package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadInboxSummaries
import com.eskerra.go.data.notes.NoteIndexError
import com.eskerra.go.data.notes.NoteIndexException
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InboxViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val loadInboxSummaries: LoadInboxSummaries
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = InboxUiState.Loading
            loadInboxSummaries(config, filesDir).fold(
                onSuccess = { notes ->
                    _uiState.value = when {
                        notes.isEmpty() -> InboxUiState.Empty
                        else -> InboxUiState.Content(notes)
                    }
                },
                onFailure = { error ->
                    _uiState.value = InboxUiState.Error(mapScanFailure(error))
                }
            )
        }
    }

    private fun mapScanFailure(error: Throwable): String {
        val indexError = (error as? NoteIndexException)?.error
        return when (indexError) {
            is NoteIndexError.InvalidWorkspacePath -> WORKSPACE_UNAVAILABLE_MESSAGE
            is NoteIndexError.WorkspaceMissing -> WORKSPACE_MISSING_MESSAGE
            is NoteIndexError.ScanFailed, null -> SCAN_ERROR_MESSAGE
        }
    }

    companion object {
        const val SCAN_ERROR_MESSAGE = "Could not scan workspace notes."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            loadInboxSummaries: LoadInboxSummaries
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InboxViewModel(config, filesDir, loadInboxSummaries) as T
        }
    }
}
