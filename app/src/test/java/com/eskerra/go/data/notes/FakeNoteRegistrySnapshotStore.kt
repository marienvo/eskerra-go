package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistrySnapshotStore
import java.io.File

/** In-memory [NoteRegistrySnapshotStore] for JVM tests. */
class FakeNoteRegistrySnapshotStore(private var registry: NoteRegistry? = null) :
    NoteRegistrySnapshotStore {

    override suspend fun read(config: WorkspaceConfig, filesDir: File): NoteRegistry? = registry

    override suspend fun save(config: WorkspaceConfig, filesDir: File, registry: NoteRegistry) {
        this.registry = registry
    }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        registry = null
    }
}
