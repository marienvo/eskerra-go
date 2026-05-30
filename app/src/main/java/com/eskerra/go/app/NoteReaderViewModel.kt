package com.eskerra.go.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadNoteForReading
import com.eskerra.go.feature.note.NoteReaderUiState
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteReaderViewModel(
    private val config: WorkspaceConfig,
    private val filesDir: File,
    private val noteId: NoteId,
    private val loadNoteForReading: LoadNoteForReading
) : ViewModel() {

    private val _uiState = MutableStateFlow<NoteReaderUiState>(NoteReaderUiState.Loading)
    val uiState: StateFlow<NoteReaderUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = NoteReaderUiState.Loading
            loadNoteForReading(config, filesDir, noteId).fold(
                onSuccess = { document ->
                    _uiState.value = NoteReaderUiState.Content(
                        title = document.note.title,
                        noteId = document.note.id,
                        path = document.content.path.value,
                        canEdit = document.note.isInbox,
                        document = document
                    )
                },
                onFailure = { error ->
                    _uiState.value = mapFailure(error)
                }
            )
        }
    }

    private fun mapFailure(error: Throwable): NoteReaderUiState {
        val contentError = (error as? NoteContentException)?.error
        return when (contentError) {
            NoteContentError.InvalidNoteId -> NoteReaderUiState.InvalidNoteId
            NoteContentError.NotFound -> NoteReaderUiState.NotFound
            NoteContentError.InvalidWorkspacePath ->
                NoteReaderUiState.Error(WORKSPACE_UNAVAILABLE_MESSAGE)
            NoteContentError.WorkspaceMissing ->
                NoteReaderUiState.Error(WORKSPACE_MISSING_MESSAGE)
            is NoteContentError.ReadFailed,
            null -> NoteReaderUiState.Error(READ_ERROR_MESSAGE)
        }
    }

    companion object {
        const val READ_ERROR_MESSAGE = "Could not open this note."
        const val WORKSPACE_UNAVAILABLE_MESSAGE = "Workspace is not available."
        const val WORKSPACE_MISSING_MESSAGE = "Workspace files are missing."

        fun factory(
            config: WorkspaceConfig,
            filesDir: File,
            noteId: NoteId,
            loadNoteForReading: LoadNoteForReading
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteReaderViewModel(config, filesDir, noteId, loadNoteForReading) as T
        }
    }
}
