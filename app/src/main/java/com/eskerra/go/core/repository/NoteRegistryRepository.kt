package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Refreshes the in-memory note registry from the configured workspace directory. */
interface NoteRegistryRepository {
    /**
     * Walks the workspace and rebuilds the registry. When [previousRegistry] is supplied, the
     * scan reuses summaries for files whose mtime + size are unchanged (incremental rescan);
     * pass `null` to force a full read of every note.
     */
    suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry? = null
    ): Result<NoteRegistry>
}
