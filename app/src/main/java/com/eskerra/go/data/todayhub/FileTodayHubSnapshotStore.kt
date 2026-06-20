package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.TodayHubSnapshotStore
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import com.eskerra.go.data.workspace.GateFingerprintComputer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File-backed current-week Today Hub snapshot for stale-while-revalidate cold start. */
class FileTodayHubSnapshotStore : TodayHubSnapshotStore {

    override suspend fun read(config: WorkspaceConfig, filesDir: File): TodayHubSnapshot? =
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            if (!snapshotFile.isFile) {
                return@withContext null
            }
            runCatching {
                TodayHubSnapshotCodec.decode(
                    raw = snapshotFile.readText(),
                    expectedFingerprint = GateFingerprintComputer.compute(config, filesDir)
                )
            }.getOrNull()
        }

    override suspend fun save(config: WorkspaceConfig, filesDir: File, snapshot: TodayHubSnapshot) {
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            snapshotFile.parentFile?.mkdirs()
            snapshotFile.writeText(
                TodayHubSnapshotCodec.encode(
                    fingerprint = GateFingerprintComputer.compute(config, filesDir),
                    savedAtEpochMs = System.currentTimeMillis(),
                    snapshot = snapshot
                )
            )
        }
    }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        withContext(Dispatchers.IO) {
            val snapshotFile = snapshotFile(filesDir)
            if (!snapshotFile.isFile) {
                return@withContext
            }
            val expected = GateFingerprintComputer.compute(config, filesDir)
            val matches = runCatching {
                TodayHubSnapshotCodec.readFingerprint(snapshotFile.readText()) == expected
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
        private const val SNAPSHOT_FILE_NAME = "today_hub_snapshot.json"
    }
}

/** Minimal JSON codec without Android-only org.json for JVM-friendly tests. */
internal object TodayHubSnapshotCodec {

    fun encode(
        fingerprint: GateFingerprint,
        savedAtEpochMs: Long,
        snapshot: TodayHubSnapshot
    ): String = buildString {
        append("{\"workspaceFingerprint\":\"")
        append(escape(fingerprint.value))
        append("\",\"savedAtEpochMs\":")
        append(savedAtEpochMs)
        append(",\"hubs\":[")
        append(snapshot.hubs.joinToString(separator = ",") { encodeHub(it) })
        append("],\"activeHubId\":\"")
        append(escape(snapshot.activeHubId.value))
        append("\",\"settings\":")
        append(encodeSettings(snapshot.settings))
        append(",\"introMarkdown\":\"")
        append(escape(snapshot.introMarkdown))
        append("\",\"availableWeekStems\":")
        append(encodeStringArray(snapshot.availableWeekStems))
        append(",\"selectedWeekStem\":\"")
        append(escape(snapshot.selectedWeekStem))
        append("\",\"row\":")
        append(snapshot.row?.let(::encodeRow) ?: "null")
        append('}')
    }

    fun readFingerprint(raw: String): GateFingerprint =
        GateFingerprint(readQuotedValue(raw, "workspaceFingerprint"))

    fun decode(raw: String, expectedFingerprint: GateFingerprint): TodayHubSnapshot {
        val fingerprint = readFingerprint(raw)
        require(fingerprint == expectedFingerprint) { "snapshot fingerprint mismatch" }
        return TodayHubSnapshot(
            hubs = readObjectArray(raw, "hubs").map { hub ->
                TodayHubRef(
                    noteId = NoteId(readQuotedValue(hub, "noteId")),
                    folderLabel = readQuotedValue(hub, "folderLabel")
                )
            },
            activeHubId = NoteId(readQuotedValue(raw, "activeHubId")),
            settings = parseSettings(readObject(raw, "settings")),
            introMarkdown = readQuotedValue(raw, "introMarkdown"),
            availableWeekStems = readStringArray(raw, "availableWeekStems"),
            selectedWeekStem = readQuotedValue(raw, "selectedWeekStem"),
            row = readNullableObject(raw, "row")?.let(::parseRow)
        )
    }

    private fun encodeHub(hub: TodayHubRef): String = buildString {
        append("{\"noteId\":\"")
        append(escape(hub.noteId.value))
        append("\",\"folderLabel\":\"")
        append(escape(hub.folderLabel))
        append("\"}")
    }

    private fun encodeSettings(settings: TodayHubFrontmatter.Settings): String = buildString {
        append("{\"perpetualType\":\"")
        append(escape(settings.perpetualType))
        append("\",\"columns\":")
        append(encodeStringArray(settings.columns))
        append(",\"start\":\"")
        append(settings.start.name)
        append("\"}")
    }

    private fun encodeRow(row: TodayHubRow): String = buildString {
        append("{\"rowNoteId\":\"")
        append(escape(row.rowNoteId.value))
        append("\",\"weekStartStem\":\"")
        append(escape(row.weekStartStem))
        append("\",\"columns\":")
        append(encodeStringArray(row.columns))
        append('}')
    }

    private fun encodeStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
            "\"${escape(value)}\""
        }

    private fun parseSettings(raw: String): TodayHubFrontmatter.Settings =
        TodayHubFrontmatter.Settings(
            perpetualType = readQuotedValue(raw, "perpetualType"),
            columns = readStringArray(raw, "columns"),
            start = TodayHubFrontmatter.StartDay.valueOf(readQuotedValue(raw, "start"))
        )

    private fun parseRow(raw: String): TodayHubRow = TodayHubRow(
        rowNoteId = NoteId(readQuotedValue(raw, "rowNoteId")),
        weekStartStem = readQuotedValue(raw, "weekStartStem"),
        columns = readStringArray(raw, "columns")
    )

    private fun readNullableObject(raw: String, key: String): String? {
        val token = "\"$key\":"
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        val valueStart = start + token.length
        return if (raw.startsWith("null", valueStart)) {
            null
        } else {
            readObjectAt(raw, valueStart, key)
        }
    }

    private fun readObject(raw: String, key: String): String {
        val token = "\"$key\":"
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        return readObjectAt(raw, start + token.length, key)
    }

    private fun readObjectAt(raw: String, start: Int, key: String): String {
        require(start < raw.length && raw[start] == '{') { "invalid $key object" }
        val end = findMatching(raw, start, '{', '}')
        return raw.substring(start, end + 1)
    }

    private fun readObjectArray(raw: String, key: String): List<String> =
        splitTopLevel(readArrayBody(raw, key), key, '{', '}')

    private fun readStringArray(raw: String, key: String): List<String> {
        val body = readArrayBody(raw, key).trim()
        if (body.isEmpty()) {
            return emptyList()
        }
        val values = mutableListOf<String>()
        var index = 0
        while (index < body.length) {
            require(body[index] == '"') { "invalid $key string array" }
            val result = readQuotedAt(body, index)
            values += result.value
            index = result.nextIndex
            if (index < body.length) {
                require(body[index] == ',') { "invalid $key string array" }
                index += 1
            }
        }
        return values
    }

    private fun readArrayBody(raw: String, key: String): String {
        val token = "\"$key\":["
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        val bodyStart = start + token.length
        val bodyEnd = findMatching(raw, bodyStart - 1, '[', ']')
        return raw.substring(bodyStart, bodyEnd)
    }

    private fun splitTopLevel(body: String, key: String, open: Char, close: Char): List<String> {
        if (body.isBlank()) {
            return emptyList()
        }
        val values = mutableListOf<String>()
        var index = 0
        while (index < body.length) {
            require(body[index] == open) { "invalid $key array" }
            val end = findMatching(body, index, open, close)
            values += body.substring(index, end + 1)
            index = end + 1
            if (index < body.length) {
                require(body[index] == ',') { "invalid $key array" }
                index += 1
            }
        }
        return values
    }

    private fun findMatching(raw: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == open -> depth += 1
                char == close -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        error("unclosed JSON value")
    }

    private fun readQuotedValue(raw: String, key: String): String {
        val token = "\"$key\":\""
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        return readQuotedAt(raw, start + token.length - 1).value
    }

    private data class QuotedResult(val value: String, val nextIndex: Int)

    private fun readQuotedAt(raw: String, quoteIndex: Int): QuotedResult {
        var index = quoteIndex + 1
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
                raw[index] == '"' -> return QuotedResult(builder.toString(), index + 1)
                else -> {
                    builder.append(raw[index])
                    index += 1
                }
            }
        }
        error("unterminated string")
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
