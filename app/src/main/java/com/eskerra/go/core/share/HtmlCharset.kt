package com.eskerra.go.core.share

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Picks the charset to decode an HTML head with: the `Content-Type` header wins, then a
 * `<meta charset>` / `<meta http-equiv="content-type">` declaration sniffed from the
 * ASCII-decoded bytes, then UTF-8. An unknown or unsupported name always falls back to UTF-8.
 */
object HtmlCharset {

    private val META_CHARSET = Regex(
        """<meta[^>]*charset\s*=\s*["']?\s*([a-zA-Z0-9_:.+-]+)""",
        RegexOption.IGNORE_CASE
    )

    fun resolve(headerCharsetName: String?, headAscii: String): Charset =
        charsetOrNull(headerCharsetName)
            ?: charsetOrNull(META_CHARSET.find(headAscii)?.groupValues?.get(1))
            ?: StandardCharsets.UTF_8

    private fun charsetOrNull(name: String?): Charset? {
        val trimmed = name?.trim()?.trim('"', '\'').orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }
        return runCatching { Charset.forName(trimmed) }.getOrNull()
    }
}
