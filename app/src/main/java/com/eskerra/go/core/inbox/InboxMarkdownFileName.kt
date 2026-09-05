package com.eskerra.go.core.inbox

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/** Mirrors `packages/eskerra-core/src/inboxMarkdown.ts` filename helpers. */
object InboxMarkdownFileName {

    const val MARKDOWN_EXTENSION = ".md"
    private const val MAX_MARKDOWN_STEM_BYTES = 252

    private val ILLEGAL_FILENAME_CHARS = setOf(
        '/', '\\', ':', '*', '?', '"', '<', '>', '|', '\'', '`',
        '\u2018', '\u2019', '\u201C', '\u201D'
    )
    private val EDGE_TRIM_CHARS = setOf('.', ' ')

    fun sanitizeInboxNoteStem(rawName: String): String? {
        val normalized = Normalizer.normalize(rawName, Normalizer.Form.NFC)
            .filter { char -> char.code > 0x1f && char.code != 0x7f && char.code !in 0x80..0x9f }
            .let(::stripIllegalFilenameChars)
            .let(::collapseAsciiWhitespaceRunsToSpace)
            .let(::trimLeadingDotsAndSpaces)
            .let(::trimTrailingDotsAndSpaces)
            .let(::prefixWindowsDeviceName)
            .let { truncateUtf8(it, MAX_MARKDOWN_STEM_BYTES) }
            .let(::trimTrailingDotsAndSpaces)
        return normalized.ifEmpty { null }
    }

    fun sanitizeFileName(
        rawName: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String = sanitizeInboxNoteStem(rawName) ?: "untitled"

    fun pickNextInboxMarkdownFileName(
        baseStem: String,
        occupiedMarkdownNames: Set<String>
    ): String {
        var candidate = fitStemWithSuffix(baseStem, "") + MARKDOWN_EXTENSION
        var nextSuffix = 2
        while (occupiedMarkdownNames.any { portableNameKey(it) == portableNameKey(candidate) }) {
            val suffix = "-$nextSuffix"
            candidate = fitStemWithSuffix(baseStem, suffix) + suffix + MARKDOWN_EXTENSION
            nextSuffix += 1
        }
        return candidate
    }

    fun markdownFileNameForStem(baseStem: String, numericSuffix: String = ""): String =
        fitStemWithSuffix(baseStem, numericSuffix) + numericSuffix + MARKDOWN_EXTENSION

    private fun stripIllegalFilenameChars(value: String): String =
        value.filterNot { it in ILLEGAL_FILENAME_CHARS }

    private fun collapseAsciiWhitespaceRunsToSpace(value: String): String {
        val builder = StringBuilder()
        var previousWasWhitespace = false
        for (char in value) {
            if (char.isWhitespace()) {
                if (!previousWasWhitespace) {
                    builder.append(' ')
                    previousWasWhitespace = true
                }
            } else {
                builder.append(char)
                previousWasWhitespace = false
            }
        }
        return builder.toString()
    }

    private fun trimLeadingDotsAndSpaces(value: String): String =
        value.dropWhile { it in EDGE_TRIM_CHARS }

    private fun trimTrailingDotsAndSpaces(value: String): String =
        value.dropLastWhile { it in EDGE_TRIM_CHARS }

    private fun prefixWindowsDeviceName(value: String): String {
        val stem = value.substringBefore('.').uppercase(Locale.ROOT)
        val reserved = stem in setOf("CON", "PRN", "AUX", "NUL") ||
            Regex("^(COM|LPT)[1-9\u00B9\u00B2\u00B3]$").matches(stem)
        return if (reserved) "_$value" else value
    }

    private fun fitStemWithSuffix(stem: String, suffix: String): String {
        val suffixBytes = suffix.toByteArray(StandardCharsets.UTF_8).size
        val availableBytes = MAX_MARKDOWN_STEM_BYTES - suffixBytes
        return truncateUtf8(stem, availableBytes)
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        val output = StringBuilder()
        var bytes = 0
        value.codePoints().forEach { codePoint ->
            val text = String(Character.toChars(codePoint))
            val size = text.toByteArray(StandardCharsets.UTF_8).size
            if (bytes + size <= maxBytes) {
                output.append(text)
                bytes += size
            }
        }
        return output.toString()
    }

    private fun portableNameKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).uppercase(Locale.ROOT)
}
