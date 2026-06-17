package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnapshotStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File-backed inbox snapshot cache for stale-while-revalidate cold start. */
class FileInboxSnapshotStore : InboxSnapshotStore {

    override suspend fun read(config: WorkspaceConfig, filesDir: File): List<NoteSummary>? =
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            if (!snapshotFile.isFile) {
                return@withContext null
            }
            runCatching {
                InboxSnapshotCodec.decode(
                    raw = snapshotFile.readText(),
                    expectedFingerprint = GateFingerprintComputer.compute(config, filesDir)
                )
            }.getOrNull()
        }

    override suspend fun save(
        config: WorkspaceConfig,
        filesDir: File,
        summaries: List<NoteSummary>
    ) = withContext(Dispatchers.IO) {
        val snapshotFile = snapshotFile(filesDir)
        snapshotFile.parentFile?.mkdirs()
        snapshotFile.writeText(
            InboxSnapshotCodec.encode(
                fingerprint = GateFingerprintComputer.compute(config, filesDir),
                savedAtEpochMs = System.currentTimeMillis(),
                summaries = summaries
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
                InboxSnapshotCodec.readFingerprint(snapshotFile.readText()) == expected
            }.getOrDefault(false)
            if (matches) {
                snapshotFile.delete()
            }
        }
    }

    override suspend fun clearAll(filesDir: File) {
        withContext(Dispatchers.IO) {
            snapshotFile(filesDir).delete()
        }
    }

    private fun snapshotFile(filesDir: File): File =
        File(File(filesDir, CACHE_DIR), SNAPSHOT_FILE_NAME)

    companion object {
        private const val CACHE_DIR = "cache"
        private const val SNAPSHOT_FILE_NAME = "inbox_snapshot.json"
    }
}

/** Minimal JSON codec without Android-only org.json for JVM-friendly tests. */
internal object InboxSnapshotCodec {

    private const val SUMMARIES_ARRAY_KEY = "summaries"

    fun encode(
        fingerprint: GateFingerprint,
        savedAtEpochMs: Long,
        summaries: List<NoteSummary>
    ): String = SnapshotNoteJsonCodec.encodeEnvelope(
        fingerprint = fingerprint,
        savedAtEpochMs = savedAtEpochMs,
        notesArrayKey = SUMMARIES_ARRAY_KEY,
        notes = summaries
    )

    fun readFingerprint(raw: String): GateFingerprint = SnapshotNoteJsonCodec.readFingerprint(raw)

    fun decode(raw: String, expectedFingerprint: GateFingerprint): List<NoteSummary> =
        SnapshotNoteJsonCodec.decodeNotesArray(
            raw = raw,
            expectedFingerprint = expectedFingerprint,
            notesArrayKey = SUMMARIES_ARRAY_KEY
        )
}
