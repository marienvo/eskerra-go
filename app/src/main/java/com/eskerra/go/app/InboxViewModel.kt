package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.LoadInboxSummaries
import com.eskerra.go.feature.inbox.InboxUiState
import java.io.File
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = InboxUiState.Loading
            loadInboxSummaries(config, filesDir).fold(
                onSuccess = { notes ->
                    _uiState.value = when {
                        notes.isEmpty() -> InboxUiState.Empty
                        else -> InboxUiState.Content(notes)
                    }
                },
                onFailure = {
                    _uiState.value = InboxUiState.Error(SCAN_ERROR_MESSAGE)
                }
            )
        }
    }

    companion object {
        const val SCAN_ERROR_MESSAGE = "Could not scan workspace notes."

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
