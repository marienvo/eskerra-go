package com.eskerra.go.core.podcast.rss

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastsMdMergeTest {

    private val zone = ZoneId.of("UTC")

    // 2026-03-15 noon UTC. today=03-15, yesterday=03-14, 7-days-ago=03-08.
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val play = "▶"

    private fun candidate(
        date: String,
        title: String,
        mp3Url: String,
        series: String = "Daily News",
        article: String? = null
    ) = RssEpisode(
        title = title,
        mp3Url = mp3Url,
        pubInstant = Instant.parse("${date}T08:00:00Z").toEpochMilli(),
        date = date,
        articleUrl = article,
        seriesName = series
    )

    @Test
    fun `retention keeps today and yesterday regardless of played state`() {
        val existing = """
            - [x] 2026-03-15; Today played [$play](https://cdn/a.mp3) (News)
            - [x] 2026-03-14; Yesterday played [$play](https://cdn/b.mp3) (News)
        """.trimIndent()

        val merged = PodcastsMdMerge.merge(existing, emptyList(), now, zone)

        assertTrue(merged.contains("https://cdn/a.mp3"))
        assertTrue(merged.contains("https://cdn/b.mp3"))
    }

    @Test
    fun `retention drops played episodes inside the seven day window but keeps unplayed`() {
        val existing = """
            - [ ] 2026-03-11; Mid unplayed [$play](https://cdn/keep.mp3) (News)
            - [x] 2026-03-11; Mid played [$play](https://cdn/drop.mp3) (News)
        """.trimIndent()

        val merged = PodcastsMdMerge.merge(existing, emptyList(), now, zone)

        assertTrue(merged.contains("https://cdn/keep.mp3"))
        assertFalse(merged.contains("https://cdn/drop.mp3"))
    }

    @Test
    fun `retention drops everything older than seven days`() {
        val existing =
            "- [ ] 2026-03-07; Too old [$play](https://cdn/old.mp3) (News)"

        val merged = PodcastsMdMerge.merge(existing, emptyList(), now, zone)

        assertFalse(merged.contains("https://cdn/old.mp3"))
    }

    @Test
    fun `candidates are added only for today and yesterday`() {
        val candidates = listOf(
            candidate("2026-03-15", "Fresh today", "https://cdn/today.mp3"),
            candidate("2026-03-14", "Fresh yesterday", "https://cdn/yesterday.mp3"),
            candidate("2026-03-13", "Two days ago", "https://cdn/old.mp3")
        )

        val merged = PodcastsMdMerge.merge("", candidates, now, zone)

        assertTrue(merged.contains("https://cdn/today.mp3"))
        assertTrue(merged.contains("https://cdn/yesterday.mp3"))
        assertFalse(merged.contains("https://cdn/old.mp3"))
        assertTrue(merged.lineSequence().all { it.isEmpty() || it.startsWith("- [ ]") })
    }

    @Test
    fun `collision merges played with OR and keeps existing article url`() {
        val existing =
            "- [x] 2026-03-15; [🌐](https://article) The Same Title! " +
                "[$play](https://cdn/keep.mp3) (News)"
        val candidates = listOf(
            candidate("2026-03-15", "the same title", "https://cdn/keep.mp3")
        )

        val merged = PodcastsMdMerge.merge(existing, candidates, now, zone)

        val episodeLines = merged.lines().filter { it.startsWith("- [") }
        assertEquals(1, episodeLines.size)
        assertTrue(episodeLines.single().startsWith("- [x]"))
        assertTrue(merged.contains("https://article"))
    }

    @Test
    fun `collision prefers mp3 url without amp entity and sanitizes output`() {
        val existing =
            "- [ ] 2026-03-15; Title [$play](https://cdn/x.mp3?a=1&amp;b=2) (News)"
        val candidates = listOf(
            candidate("2026-03-15", "Title", "https://cdn/x.mp3?a=1&b=2")
        )

        val merged = PodcastsMdMerge.merge(existing, candidates, now, zone)

        val episodeLines = merged.lines().filter { it.startsWith("- [") }
        assertEquals(1, episodeLines.size)
        assertFalse(merged.contains("&amp;"))
        assertTrue(merged.contains("https://cdn/x.mp3?a=1&b=2"))
    }

    @Test
    fun `output sorts by descending date then ascending lowercase mp3 url`() {
        val candidates = listOf(
            candidate("2026-03-14", "B yesterday", "https://cdn/zzz.mp3"),
            candidate("2026-03-15", "A today two", "https://cdn/b.mp3"),
            candidate("2026-03-15", "A today one", "https://cdn/a.mp3")
        )

        val merged = PodcastsMdMerge.merge("", candidates, now, zone)

        val orderedUrls = merged.lines()
            .filter { it.startsWith("- [") }
            .map { line -> line.substringAfter("[$play](").substringBefore(")") }
        assertEquals(
            listOf("https://cdn/a.mp3", "https://cdn/b.mp3", "https://cdn/zzz.mp3"),
            orderedUrls
        )
    }

    @Test
    fun `non-episode prefix lines are preserved`() {
        val existing = """
            # News Episodes

            - [ ] 2026-03-15; Keep [$play](https://cdn/keep.mp3) (News)
        """.trimIndent()

        val merged = PodcastsMdMerge.merge(existing, emptyList(), now, zone)

        assertTrue(merged.startsWith("# News Episodes"))
        assertTrue(merged.contains("https://cdn/keep.mp3"))
    }
}
