package com.eskerra.go.core.model

/** Note content and editability metadata for the editor screen. */
data class EditableNote(
    val id: NoteId,
    val path: NotePath,
    val title: String,
    val markdown: String,
    val isInbox: Boolean,
    val canEdit: Boolean
)
