package com.eskerra.go.core.podcast.rss

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Refreshes a single `📻` markdown file: fetches its feed(s), dedupes items, and
 * rebuilds the body, bumping only `rssFetchedAt` on success.
 *
 * Fetching is delegated to [fetch] (a blocking `url, timeoutMs -> xml?` function)
 * so this orchestration stays pure and JVM-testable. When the file has no feed
 * URLs, every fetch fails, or no items parse, the file is returned **unchanged**
 * (no `rssFetchedAt` bump) per spec §7.3.1.
 */
class PodcastRssFileSync(
    private val fetch: (url: String, timeoutMs: Long) -> String?,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    fun sync(fileName: String, content: String): RssFileSyncResult {
        val document = RssFrontmatterParser.parse(content)
        val showTitle = extractRssPodcastTitle(fileName, document.body)
        val unchanged = RssFileSyncResult(
            changed = false,
            content = content,
            episodes = emptyList(),
            showTitle = showTitle
        )

        if (document.frontmatter.feedUrls.isEmpty()) return unchanged

        val rawItems = mutableListOf<RawRssItem>()
        var anyFetchSucceeded = false
        for (url in document.frontmatter.feedUrls) {
            val xml = fetch(url, document.frontmatter.timeoutMs) ?: continue
            anyFetchSucceeded = true
            rawItems += RssFeedParser.parse(xml)
        }
        if (!anyFetchSucceeded || rawItems.isEmpty()) return unchanged

        val episodes = dedupeByMp3Url(rawItems).map { item ->
            RssEpisode(
                title = item.title,
                mp3Url = item.mp3Url,
                pubInstant = item.pubInstant,
                date = RssCalendar.isoDate(item.pubInstant, zoneId),
                articleUrl = item.articleUrl,
                seriesName = showTitle
            )
        }

        val now = nowMs()
        val body = RssMarkdownComposer.compose(
            showTitle = showTitle,
            episodes = episodes,
            daysAgo = document.frontmatter.daysAgo,
            nowMs = now,
            zoneId = zoneId
        )
        val frontmatterLines = RssFrontmatterParser.withFetchedAt(
            document.frontmatterLines,
            isoUtc(now)
        )
        val newContent = RssFrontmatterParser.render(frontmatterLines, body)
        return RssFileSyncResult(
            changed = newContent != content,
            content = newContent,
            episodes = episodes,
            showTitle = showTitle
        )
    }

    private fun dedupeByMp3Url(items: List<RawRssItem>): List<RawRssItem> {
        val byKey = LinkedHashMap<String, RawRssItem>()
        for (item in items) {
            val key = item.mp3Url.lowercase()
            val existing = byKey[key]
            if (existing == null || item.pubInstant > existing.pubInstant) {
                byKey[key] = item
            }
        }
        return byKey.values.toList()
    }

    companion object {
        private val ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)

        fun isoUtc(epochMillis: Long): String = ISO_UTC.format(Instant.ofEpochMilli(epochMillis))

        /** H1 from the body, else the filename stem with `📻`/`.md` stripped. */
        fun extractRssPodcastTitle(fileName: String, body: String): String {
            val heading = body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("# ") }
                ?.removePrefix("# ")
                ?.trim()
            if (!heading.isNullOrEmpty()) return heading

            val stem = fileName.substringAfterLast('/')
                .substringAfterLast('\\')
                .removeSuffix(".md")
                .removeSuffix(".MD")
            return stem.removePrefix("📻").trim().ifEmpty { stem.trim() }
        }
    }
}
