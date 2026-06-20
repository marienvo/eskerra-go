package com.eskerra.go.core.podcast.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssChannelArtworkParserTest {

    @Test
    fun parseArtworkUrl_readsItunesImageHref() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <title>Demo</title>
                <itunes:image href="https://cdn.example.com/cover.jpg"/>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals(
            "https://cdn.example.com/cover.jpg",
            RssChannelArtworkParser.parseArtworkUrl(xml)
        )
    }

    @Test
    fun parseArtworkUrl_readsRssImageUrl() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Demo</title>
                <image>
                  <url>https://cdn.example.com/rss-image.png</url>
                </image>
              </channel>
            </rss>
        """.trimIndent()

        assertEquals(
            "https://cdn.example.com/rss-image.png",
            RssChannelArtworkParser.parseArtworkUrl(xml)
        )
    }

    @Test
    fun parseArtworkUrl_returnsNullForInvalidXml() {
        assertNull(RssChannelArtworkParser.parseArtworkUrl("not xml"))
    }
}
