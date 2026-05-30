package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Refreshes the in-memory note registry from the configured workspace directory. */
interface NoteRegistryRepository {
    suspend fun refresh(config: WorkspaceConfig, filesDir: File): Result<NoteRegistry>
}
