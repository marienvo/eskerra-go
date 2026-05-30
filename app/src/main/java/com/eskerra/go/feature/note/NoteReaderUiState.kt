package com.eskerra.go.feature.note

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderDocument

sealed interface NoteReaderUiState {
    data object Loading : NoteReaderUiState

    data class Content(
        val title: String,
        val noteId: NoteId,
        val path: String,
        val canEdit: Boolean,
        val document: NoteReaderDocument
    ) : NoteReaderUiState

    data object NotFound : NoteReaderUiState

    data object InvalidNoteId : NoteReaderUiState

    data class Error(val message: String) : NoteReaderUiState
}
