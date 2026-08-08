package com.eskerra.go.core.share

import java.net.URI

/**
 * Recognizes shares that are exactly one web URL.
 *
 * The bar is deliberately high: line 1 of an inbox draft becomes the note's h1 **and its
 * filename** (see `InboxNoteDraft`), so a URL must never end up there. Anything mixed —
 * a link inside prose, a title on its own line — stays plain text, and the user types the
 * title themselves.
 */
object SharedUrl {

    const val MAX_URL_LENGTH = 2048

    private const val LOCALHOST = "localhost"

    /** The whole trimmed [text] is a single http/https URL, or null. */
    fun soleUrlOrNull(text: String): String? {
        val trimmed = text.trim()
        return trimmed.takeIf { looksLikeUrl(it) }
    }

    /** True when [line] on its own is a usable http/https URL. */
    fun looksLikeUrl(line: String): Boolean {
        val candidate = line.trim()
        if (candidate.isEmpty() || candidate.length > MAX_URL_LENGTH) {
            return false
        }
        if (candidate.any { it.isWhitespace() }) {
            return false
        }
        val lower = candidate.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false
        }
        val host = runCatching { URI(candidate).host }.getOrNull().orEmpty()
        return host.isNotBlank() &&
            (host.contains('.') || host.equals(LOCALHOST, ignoreCase = true))
    }
}
