package com.eskerra.go.core.podcast.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFrontmatterParserTest {

    @Test
    fun `parses scalar feed url with default daysAgo and timeout`() {
        val doc = RssFrontmatterParser.parse(
            """
            ---
            rssFeedUrl: https://feed.example/rss.xml
            ---
            # Show
            """.trimIndent()
        )

        assertEquals(listOf("https://feed.example/rss.xml"), doc.frontmatter.feedUrls)
        assertEquals(RssFrontmatter.DEFAULT_DAYS_AGO, doc.frontmatter.daysAgo)
        assertEquals(RssFrontmatter.DEFAULT_TIMEOUT_MS, doc.frontmatter.timeoutMs)
        assertEquals("# Show", doc.body)
    }

    @Test
    fun `parses yaml list feed urls and custom numeric fields`() {
        val doc = RssFrontmatterParser.parse(
            """
            ---
            rssFeedUrl:
              - https://a.example/rss
              - "https://b.example/rss"
            daysAgo: 3
            timeoutMs: 5000
            minFetchIntervalMinutes: 30
            ---
            body
            """.trimIndent()
        )

        assertEquals(
            listOf("https://a.example/rss", "https://b.example/rss"),
            doc.frontmatter.feedUrls
        )
        assertEquals(3, doc.frontmatter.daysAgo)
        assertEquals(5000L, doc.frontmatter.timeoutMs)
    }

    @Test
    fun `missing frontmatter yields no feed urls and whole content as body`() {
        val doc = RssFrontmatterParser.parse("# Just a title\nbody")

        assertTrue(doc.frontmatter.feedUrls.isEmpty())
        assertEquals("# Just a title\nbody", doc.body)
    }

    @Test
    fun `withFetchedAt replaces existing entry in place`() {
        val lines = listOf("rssFeedUrl: https://x", "rssFetchedAt: old")

        val updated = RssFrontmatterParser.withFetchedAt(lines, "2026-03-15T00:00:00Z")

        assertEquals(
            listOf("rssFeedUrl: https://x", "rssFetchedAt: 2026-03-15T00:00:00Z"),
            updated
        )
    }

    @Test
    fun `withFetchedAt appends when absent and render round-trips`() {
        val lines = RssFrontmatterParser.withFetchedAt(
            listOf("rssFeedUrl: https://x"),
            "2026-03-15T00:00:00Z"
        )
        val rendered = RssFrontmatterParser.render(lines, "# Body")

        val reparsed = RssFrontmatterParser.parse(rendered)
        assertEquals(listOf("https://x"), reparsed.frontmatter.feedUrls)
        assertTrue(rendered.contains("rssFetchedAt: 2026-03-15T00:00:00Z"))
        assertEquals("# Body", reparsed.body)
    }
}
