package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
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

    fun encode(
        fingerprint: GateFingerprint,
        savedAtEpochMs: Long,
        summaries: List<NoteSummary>
    ): String {
        val notes = summaries.joinToString(separator = ",") { summary ->
            buildString {
                append('{')
                append("\"id\":\"").append(escape(summary.id.value)).append('"')
                append(",\"title\":\"").append(escape(summary.title)).append('"')
                append(",\"snippet\":\"").append(escape(summary.snippet)).append('"')
                append(",\"isInbox\":").append(summary.isInbox)
                append(",\"lastModifiedEpochMillis\":").append(summary.lastModifiedEpochMillis)
                append('}')
            }
        }
        return buildString {
            append("{\"workspaceFingerprint\":\"")
            append(escape(fingerprint.value))
            append("\",\"savedAtEpochMs\":")
            append(savedAtEpochMs)
            append(",\"summaries\":[")
            append(notes)
            append("]}")
        }
    }

    fun readFingerprint(raw: String): GateFingerprint =
        GateFingerprint(readQuotedValue(raw, "workspaceFingerprint"))

    fun decode(raw: String, expectedFingerprint: GateFingerprint): List<NoteSummary> {
        val fingerprint = readFingerprint(raw)
        require(fingerprint == expectedFingerprint) { "snapshot fingerprint mismatch" }
        val arrayStart = raw.indexOf("\"summaries\":[")
        require(arrayStart >= 0) { "missing summaries" }
        val contentStart = arrayStart + "\"summaries\":[".length
        val contentEnd = raw.lastIndexOf(']')
        require(contentEnd > contentStart) { "invalid summaries array" }
        val body = raw.substring(contentStart, contentEnd).trim()
        if (body.isEmpty()) {
            return emptyList()
        }
        return body.split("},{").map { chunk ->
            val normalized = when {
                chunk.startsWith('{') && chunk.endsWith('}') -> chunk
                chunk.startsWith('{') -> "$chunk}"
                chunk.endsWith('}') -> "{$chunk"
                else -> "{$chunk}"
            }
            parseSummary(normalized)
        }
    }

    private fun parseSummary(raw: String): NoteSummary = NoteSummary(
        id = NoteId(readQuotedValue(raw, "id")),
        title = readQuotedValue(raw, "title"),
        snippet = readQuotedValue(raw, "snippet"),
        isInbox = readBoolean(raw, "isInbox"),
        lastModifiedEpochMillis = readLong(raw, "lastModifiedEpochMillis")
    )

    private fun readQuotedValue(raw: String, key: String): String {
        val token = "\"$key\":\""
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        var index = start + token.length
        val builder = StringBuilder()
        while (index < raw.length) {
            when {
                raw[index] == '\\' && index + 1 < raw.length -> {
                    builder.append(
                        when (raw[index + 1]) {
                            '\\' -> '\\'
                            '"' -> '"'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> raw[index + 1]
                        }
                    )
                    index += 2
                }
                raw[index] == '"' -> break
                else -> {
                    builder.append(raw[index])
                    index += 1
                }
            }
        }
        return builder.toString()
    }

    private fun readLong(raw: String, key: String): Long {
        val token = "\"$key\":"
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        val valueStart = start + token.length
        val valueEnd = raw.indexOf(',', valueStart).let { comma ->
            if (comma == -1) raw.indexOf('}', valueStart) else comma
        }
        require(valueEnd > valueStart) { "invalid $key" }
        return raw.substring(valueStart, valueEnd).trim().toLong()
    }

    private fun readBoolean(raw: String, key: String): Boolean {
        val token = "\"$key\":"
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        val valueStart = start + token.length
        val valueEnd = raw.indexOf(',', valueStart).let { comma ->
            if (comma == -1) raw.indexOf('}', valueStart) else comma
        }
        require(valueEnd > valueStart) { "invalid $key" }
        return raw.substring(valueStart, valueEnd).trim().toBooleanStrict()
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
