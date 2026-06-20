package com.eskerra.go.core.podcast.rss

/**
 * One episode parsed from an RSS feed (and, after composing, the unit the stub
 * merge consumes). [pubInstant] is the epoch-millis publish time; [date] is the
 * local-calendar ISO date derived from it. [seriesName] is the owning show's
 * display title (used when a candidate is promoted into a stub episode line).
 */
data class RssEpisode(
    val title: String,
    val mp3Url: String,
    val pubInstant: Long,
    val date: String,
    val articleUrl: String?,
    val seriesName: String
)

/** Parsed `📻` frontmatter relevant to native RSS sync (spec §7.3.1). */
data class RssFrontmatter(
    /** All feed URLs, in declaration order (scalar collapses to a single entry). */
    val feedUrls: List<String>,
    /** Retention window in days; defaults to 7 when absent or unparseable. */
    val daysAgo: Int,
    /** Per-feed fetch timeout in ms; defaults to 8000 when absent or unparseable. */
    val timeoutMs: Long
) {
    companion object {
        const val DEFAULT_DAYS_AGO = 7
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}

/** Result of refreshing a single `📻` file. */
data class RssFileSyncResult(
    /** True when the body or `rssFetchedAt` changed and the file must be rewritten. */
    val changed: Boolean,
    /** Full markdown to persist (equal to the input when [changed] is false). */
    val content: String,
    /** Episodes composed into the refreshed body (empty when unchanged). */
    val episodes: List<RssEpisode>,
    /** Show display title resolved for this file. */
    val showTitle: String
)
