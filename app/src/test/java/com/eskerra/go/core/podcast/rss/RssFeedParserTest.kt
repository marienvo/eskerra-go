package com.eskerra.go.core.podcast.rss

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFeedParserTest {

    @Test
    fun `parses audio items with title link and pub date`() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <item>
                <title>First episode</title>
                <link>https://show.example/1</link>
                <pubDate>Sun, 15 Mar 2026 09:00:00 +0000</pubDate>
                <enclosure url="https://cdn.example/1.mp3" type="audio/mpeg" length="1"/>
              </item>
            </channel></rss>
        """.trimIndent()

        val items = RssFeedParser.parse(xml)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("First episode", item.title)
        assertEquals("https://cdn.example/1.mp3", item.mp3Url)
        assertEquals("https://show.example/1", item.articleUrl)
        assertEquals("2026-03-15", RssFeedParser.isoDateOf(item.pubInstant, ZoneId.of("UTC")))
    }

    @Test
    fun `skips items without an audio enclosure or pub date`() {
        val xml = """
            <rss><channel>
              <item><title>No audio</title><pubDate>Sun, 15 Mar 2026 09:00:00 +0000</pubDate></item>
              <item>
                <title>No date</title>
                <enclosure url="https://cdn/x.mp3" type="audio/mpeg"/>
              </item>
              <item>
                <title>Good</title>
                <pubDate>Sun, 15 Mar 2026 09:00:00 +0000</pubDate>
                <enclosure url="https://cdn/good.mp3" type="audio/mpeg"/>
              </item>
            </channel></rss>
        """.trimIndent()

        val items = RssFeedParser.parse(xml)

        assertEquals(listOf("https://cdn/good.mp3"), items.map { it.mp3Url })
    }

    @Test
    fun `malformed xml yields empty list`() {
        assertTrue(RssFeedParser.parse("not xml <<<").isEmpty())
    }
}
