package com.eskerra.go.core.podcast.rss

/**
 * Shared link/title helpers for podcast markdown, mirroring
 * `PodcastMarkdownLinks.kt` in the reference Android RSS sync (spec §7.3.1).
 */
object PodcastMarkdownLinks {

    private const val AMP_ENTITY = "&amp;"

    /**
     * Dedupe key fragment: lowercase ASCII letters and digits only. Mirrors
     * `normalizeTitleKey`. An empty result falls back to `_empty:{hashCode}` so
     * distinct empty-normalizing titles do not collapse together.
     */
    fun normalizeTitleKey(title: String): String {
        val normalized = buildString {
            title.forEach { ch ->
                when (ch) {
                    in 'a'..'z' -> append(ch)
                    in 'A'..'Z' -> append(ch + 32)
                    in '0'..'9' -> append(ch)
                }
            }
        }
        return normalized.ifEmpty { "_empty:${title.hashCode()}" }
    }

    /** Replaces the literal `&amp;` entity with `&` in a link destination. */
    fun sanitizeUrl(url: String): String = url.replace(AMP_ENTITY, "&")

    /** True when [url] still carries a literal `&amp;` entity. */
    fun hasAmpEntity(url: String): Boolean = url.contains(AMP_ENTITY)
}
