package com.eskerra.go.data.notes

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/**
 * Shared minimal JSON helpers for fingerprint-keyed note snapshot files. JVM-friendly (no
 * Android-only org.json) so inbox and full-registry codecs share one implementation.
 */
internal object SnapshotNoteJsonCodec {

    private const val ID_TOKEN = "\"id\":\""
    private const val TITLE_TOKEN = ",\"title\":\""
    private const val SNIPPET_TOKEN = ",\"snippet\":\""
    private const val IS_INBOX_TOKEN = ",\"isInbox\":"
    private const val LAST_MODIFIED_TOKEN = ",\"lastModifiedEpochMillis\":"
    private const val SIZE_BYTES_TOKEN = ",\"sizeBytes\":"

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
        return NotesArrayCursor(raw, contentStart, notesArrayKey).parse()
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

    private fun readQuotedValue(raw: String, key: String): String {
        val token = "\"$key\":\""
        val start = raw.indexOf(token)
        require(start >= 0) { "missing $key" }
        var index = start + token.length
        val valueStart = index
        var firstEscape = -1
        while (index < raw.length) {
            when {
                raw[index] == '\\' && index + 1 < raw.length -> {
                    if (firstEscape < 0) firstEscape = index
                    index += 2
                }
                raw[index] == '"' ->
                    return if (firstEscape < 0) {
                        raw.substring(valueStart, index)
                    } else {
                        unescape(raw, valueStart, index)
                    }
                else -> index += 1
            }
        }
        error("unterminated $key")
    }

    private fun unescape(raw: String, start: Int, end: Int): String = buildString(end - start) {
        var index = start
        while (index < end) {
            if (raw[index] != '\\') {
                append(raw[index++])
                continue
            }
            require(index + 1 < end) { "unterminated escape" }
            append(
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
    }

    private class NotesArrayCursor(
        private val raw: String,
        start: Int,
        private val notesArrayKey: String
    ) {
        private var index = start

        fun parse(): List<NoteSummary> {
            val notes = ArrayList<NoteSummary>()
            skipWhitespace()
            if (take(']')) return notes
            while (true) {
                notes += parseSummary()
                skipWhitespace()
                when {
                    take(',') -> skipWhitespace()
                    take(']') -> return notes
                    else -> invalidArray()
                }
            }
        }

        private fun parseSummary(): NoteSummary {
            expect('{')
            expect(ID_TOKEN, "id")
            val id = readString("id")
            expect(TITLE_TOKEN, "title")
            val title = readString("title")
            expect(SNIPPET_TOKEN, "snippet")
            val snippet = readString("snippet")
            expect(IS_INBOX_TOKEN, "isInbox")
            val isInbox = readBoolean()
            expect(LAST_MODIFIED_TOKEN, "lastModifiedEpochMillis")
            val lastModified = readLong()
            val sizeBytes = if (matches(SIZE_BYTES_TOKEN)) {
                index += SIZE_BYTES_TOKEN.length
                readLong()
            } else {
                0L
            }
            expect('}')
            return NoteSummary(
                id = NoteId(id),
                title = title,
                snippet = snippet,
                isInbox = isInbox,
                lastModifiedEpochMillis = lastModified,
                sizeBytes = sizeBytes
            )
        }

        private fun readString(key: String): String {
            val start = index
            var firstEscape = -1
            while (index < raw.length) {
                when {
                    raw[index] == '\\' -> {
                        if (firstEscape < 0) firstEscape = index
                        index += 2
                    }
                    raw[index] == '"' -> {
                        val end = index++
                        return if (firstEscape < 0) {
                            raw.substring(start, end)
                        } else {
                            unescape(raw, start, end)
                        }
                    }
                    else -> index += 1
                }
            }
            error("unterminated $key")
        }

        private fun readBoolean(): Boolean = when {
            matches("true") -> true.also { index += 4 }
            matches("false") -> false.also { index += 5 }
            else -> invalidArray()
        }

        private fun readLong(): Long {
            val start = index
            if (index < raw.length && raw[index] == '-') index += 1
            while (index < raw.length && raw[index].isDigit()) index += 1
            require(index > start && !(index == start + 1 && raw[start] == '-')) {
                "invalid $notesArrayKey array"
            }
            return raw.substring(start, index).toLong()
        }

        private fun skipWhitespace() {
            while (index < raw.length && raw[index].isWhitespace()) index += 1
        }

        private fun take(char: Char): Boolean =
            (index < raw.length && raw[index] == char).also { matched ->
                if (matched) index += 1
            }

        private fun expect(char: Char) {
            if (!take(char)) invalidArray()
        }

        private fun expect(token: String, key: String) {
            require(matches(token)) { "missing $key" }
            index += token.length
        }

        private fun matches(token: String): Boolean = raw.startsWith(token, index)

        private fun invalidArray(): Nothing = error("invalid $notesArrayKey array")
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
