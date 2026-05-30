package com.eskerra.go.core.model

/** Loaded markdown body for a single note. */
data class NoteContent(val id: NoteId, val path: NotePath, val markdown: String)
