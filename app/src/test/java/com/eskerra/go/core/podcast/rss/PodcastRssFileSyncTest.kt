package com.eskerra.go.core.podcast.rss

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastRssFileSyncTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()

    private val fileName = "📻 Daily News.md"
    private val content = """
        ---
        rssFeedUrl: https://feed.example/rss.xml
        ---
        # Daily News
    """.trimIndent()

    private fun item(title: String, url: String, pubDate: String) = """
        <item>
          <title>$title</title>
          <pubDate>$pubDate</pubDate>
          <enclosure url="$url" type="audio/mpeg"/>
        </item>
    """.trimIndent()

    private fun feed(vararg items: String): String =
        "<rss><channel>${items.joinToString("")}</channel></rss>"

    @Test
    fun `returns file unchanged when every fetch fails`() {
        val sync = PodcastRssFileSync(fetch = { _, _ -> null }, nowMs = { now }, zoneId = zone)

        val result = sync.sync(fileName, content)

        assertFalse(result.changed)
        assertEquals(content, result.content)
        assertTrue(result.episodes.isEmpty())
    }

    @Test
    fun `returns file unchanged when feed has no parseable items`() {
        val sync = PodcastRssFileSync(
            fetch = { _, _ -> "<rss><channel></channel></rss>" },
            nowMs = { now },
            zoneId = zone
        )

        val result = sync.sync(fileName, content)

        assertFalse(result.changed)
        assertEquals(content, result.content)
    }

    @Test
    fun `rebuilds body bumps fetchedAt dedupes by mp3 url and applies cutoff`() {
        val xml = feed(
            item("Today old copy", "https://cdn/today.mp3", "Sun, 15 Mar 2026 06:00:00 +0000"),
            item("Today new copy", "https://cdn/today.mp3", "Sun, 15 Mar 2026 10:00:00 +0000"),
            item("Too old", "https://cdn/old.mp3", "Sat, 07 Mar 2026 10:00:00 +0000")
        )
        val sync = PodcastRssFileSync(fetch = { _, _ -> xml }, nowMs = { now }, zoneId = zone)

        val result = sync.sync(fileName, content)

        assertTrue(result.changed)
        assertTrue(result.content.contains("rssFetchedAt: 2026-03-15T12:00:00Z"))
        // Deduped to the newest copy only.
        assertEquals(1, result.content.split("https://cdn/today.mp3").size - 1)
        assertTrue(result.content.contains("Today new copy"))
        assertFalse(result.content.contains("Today old copy"))
        // Older-than-daysAgo item omitted from the body.
        assertFalse(result.content.contains("https://cdn/old.mp3"))
        assertEquals("Daily News", result.showTitle)
        assertTrue(result.episodes.all { it.seriesName == "Daily News" })
    }
}
