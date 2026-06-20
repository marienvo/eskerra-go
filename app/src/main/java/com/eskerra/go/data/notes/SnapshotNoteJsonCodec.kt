package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/**
 * Shared minimal JSON helpers for fingerprint-keyed note snapshot files. JVM-friendly (no
 * Android-only org.json) so inbox and full-registry codecs share one implementation.
 */
internal object SnapshotNoteJsonCodec {

    fun readFingerprint(raw: String): GateFingerprint =
        GateFingerprint(readQuotedValue(raw, "workspaceFingerprint"))

    fun encodeEnvelope(
        fingerprint: GateFingerprint,
        savedAtEpochMs: Long,
        notesArrayKey: String,
        notes: List<NoteSummary>
    ): String {
        val encodedNotes = notes.joinToString(separator = ",") { summary ->
            encodeNoteObject(summary)
        }
        return buildString {
            append("{\"workspaceFingerprint\":\"")
            append(escape(fingerprint.value))
            append("\",\"savedAtEpochMs\":")
            append(savedAtEpochMs)
            append(",\"")
            append(notesArrayKey)
            append("\":[")
            append(encodedNotes)
            append("]}")
        }
    }

    fun decodeNotesArray(
        raw: String,
        expectedFingerprint: GateFingerprint,
        notesArrayKey: String
    ): List<NoteSummary> {
        val fingerprint = readFingerprint(raw)
        require(fingerprint == expectedFingerprint) { "snapshot fingerprint mismatch" }
        val arrayToken = "\"$notesArrayKey\":["
        val arrayStart = raw.indexOf(arrayToken)
        require(arrayStart >= 0) { "missing $notesArrayKey" }
        val contentStart = arrayStart + arrayToken.length
        val contentEnd = raw.lastIndexOf(']')
        require(contentEnd >= contentStart) { "invalid $notesArrayKey array" }
        val body = raw.substring(contentStart, contentEnd).trim()
        if (body.isEmpty()) {
            return emptyList()
        }
        return splitTopLevelObjects(body, notesArrayKey).map(::parseSummary)
    }

    private fun encodeNoteObject(summary: NoteSummary): String = buildString {
        append('{')
        append("\"id\":\"").append(escape(summary.id.value)).append('"')
        append(",\"title\":\"").append(escape(summary.title)).append('"')
        append(",\"snippet\":\"").append(escape(summary.snippet)).append('"')
        append(",\"isInbox\":").append(summary.isInbox)
        append(",\"lastModifiedEpochMillis\":").append(summary.lastModifiedEpochMillis)
        append(",\"sizeBytes\":").append(summary.sizeBytes)
        append('}')
    }

    /** Splits a JSON array body into top-level `{...}` objects, respecting quoted strings. */
    private fun splitTopLevelObjects(body: String, notesArrayKey: String): List<String> {
        if (body.isEmpty()) {
            return emptyList()
        }
        val objects = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var start = -1
        body.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> {
                    if (depth == 0) {
                        start = index
                    }
                    depth += 1
                }
                char == '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        objects.add(body.substring(start, index + 1))
                        start = -1
                    }
                }
            }
        }
        require(depth == 0 && objects.isNotEmpty()) { "invalid $notesArrayKey array" }
        return objects
    }

    private fun parseSummary(raw: String): NoteSummary = NoteSummary(
        id = NoteId(readQuotedValue(raw, "id")),
        title = readQuotedValue(raw, "title"),
        snippet = readQuotedValue(raw, "snippet"),
        isInbox = readBoolean(raw, "isInbox"),
        lastModifiedEpochMillis = readLong(raw, "lastModifiedEpochMillis"),
        sizeBytes = readOptionalLong(raw, "sizeBytes")
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

    private fun readOptionalLong(raw: String, key: String, default: Long = 0L): Long {
        val token = "\"$key\":"
        val start = raw.indexOf(token)
        if (start < 0) {
            return default
        }
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
