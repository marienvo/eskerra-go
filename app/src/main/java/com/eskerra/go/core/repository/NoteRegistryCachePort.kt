package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** Cache abstraction for the shared note registry; implemented by `NoteRegistryCache`. */
interface NoteRegistryCachePort {
    /** Shared in-memory registry; `null` until the first successful [current]/[refresh], or after [invalidate]. */
    val registry: StateFlow<NoteRegistry?>

    suspend fun current(config: WorkspaceConfig, filesDir: File): NoteRegistry?
    suspend fun refresh(config: WorkspaceConfig, filesDir: File): Result<NoteRegistry>
    suspend fun invalidate(config: WorkspaceConfig, filesDir: File)
}
