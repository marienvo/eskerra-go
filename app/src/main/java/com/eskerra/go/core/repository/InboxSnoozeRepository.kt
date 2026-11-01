package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import java.time.Instant

interface InboxSnoozeRepository {
    suspend fun snooze(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId,
        until: Instant
    ): Result<Unit>
}
