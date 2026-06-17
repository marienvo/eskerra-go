package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistrySnapshotStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File-backed full note-registry snapshot for stale-while-revalidate cold start. */
class FileNoteRegistrySnapshotStore : NoteRegistrySnapshotStore {

    override suspend fun read(config: WorkspaceConfig, filesDir: File): NoteRegistry? =
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            if (!snapshotFile.isFile) {
                return@withContext null
            }
            runCatching {
                NoteRegistrySnapshotCodec.decode(
                    raw = snapshotFile.readText(),
                    expectedFingerprint = GateFingerprintComputer.compute(config, filesDir)
                )
            }.getOrNull()
        }

    override suspend fun save(config: WorkspaceConfig, filesDir: File, registry: NoteRegistry) =
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            snapshotFile.parentFile?.mkdirs()
            snapshotFile.writeText(
                NoteRegistrySnapshotCodec.encode(
                    fingerprint = GateFingerprintComputer.compute(config, filesDir),
                    savedAtEpochMs = System.currentTimeMillis(),
                    registry = registry
                )
            )
        }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            if (!snapshotFile.isFile) {
                return@withContext
            }
            val expected = GateFingerprintComputer.compute(config, filesDir)
            val matches = runCatching {
                NoteRegistrySnapshotCodec.readFingerprint(snapshotFile.readText()) == expected
            }.getOrDefault(false)
            if (matches) {
                snapshotFile.delete()
            }
        }
    }

    private fun snapshotFile(filesDir: File): File =
        File(File(filesDir, CACHE_DIR), SNAPSHOT_FILE_NAME)

    companion object {
        private const val CACHE_DIR = "cache"
        internal const val SNAPSHOT_FILE_NAME = "note_registry_snapshot.json"
    }
}
