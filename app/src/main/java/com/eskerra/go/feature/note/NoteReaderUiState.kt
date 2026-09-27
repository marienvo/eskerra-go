package com.eskerra.go.feature.note

import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderDocument

sealed interface NoteReaderUiState {
    data object Loading : NoteReaderUiState

    data class Content(
        val title: String,
        val noteId: NoteId,
        val path: String,
        val canEdit: Boolean,
        val document: NoteReaderDocument,
        /** [document]'s markdown with the leading `# ` title heading removed (already shown as [title]). */
        val bodyMarkdown: String,
        /**
         * [bodyMarkdown] pre-parsed off the main thread before this state is published, so title and
         * body always reach the screen in the same frame — never title-first, never an empty flash on
         * a warm hit. [com.eskerra.go.ui.markdown.VaultMarkdownView] renders it directly instead of
         * re-deriving it from the shared parse cache.
         */
        val preparedBody: PreparedMarkdown
    ) : NoteReaderUiState

    data object NotFound : NoteReaderUiState

    data object InvalidNoteId : NoteReaderUiState

    data class Error(val message: String) : NoteReaderUiState
}
