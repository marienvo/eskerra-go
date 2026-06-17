package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/**
 * Persists the full note registry for stale-while-revalidate cold start, keyed by the local
 * workspace fingerprint. Mirrors [InboxSnapshotStore] but stores every note, not just the inbox.
 * The concrete file-backed codec arrives in a later phase; [NoteRegistryCache] consumes this seam.
 */
interface NoteRegistrySnapshotStore {
    suspend fun read(config: WorkspaceConfig, filesDir: File): NoteRegistry?

    suspend fun save(config: WorkspaceConfig, filesDir: File, registry: NoteRegistry)

    suspend fun clear(config: WorkspaceConfig, filesDir: File)
}
