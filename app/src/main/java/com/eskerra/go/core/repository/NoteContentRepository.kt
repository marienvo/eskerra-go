package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Loads the markdown body for a single note from the configured workspace. */
interface NoteContentRepository {
    suspend fun load(config: WorkspaceConfig, filesDir: File, noteId: NoteId): Result<NoteContent>
}
