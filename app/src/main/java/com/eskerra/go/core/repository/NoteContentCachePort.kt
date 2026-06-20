package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteId

/** Cache abstraction for note content; implemented by `NoteContentCache`. */
interface NoteContentCachePort : NoteContentRepository {
    suspend fun evict(noteId: NoteId)
    suspend fun evictAll()
}
