package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Re-reads a dated note and changes only its first-H1 read marker. */
interface DatedNoteReadStatusRepository {
    suspend fun setRead(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        read: Boolean
    ): Result<String>
}
