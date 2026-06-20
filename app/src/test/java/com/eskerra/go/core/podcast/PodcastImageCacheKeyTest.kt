package com.eskerra.go.core.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastImageCacheKeyTest {

    @Test
    fun cacheKey_isStableForNormalizedUrl() {
        val first = podcastImageCacheKey("HTTPS://Example.com/feed.xml")
        val second = podcastImageCacheKey("  https://example.com/feed.xml  ")
        assertEquals(first, second)
        assertEquals(true, first.startsWith("rss-"))
    }

    @Test
    fun memoryKey_includesWorkspaceAndCacheKey() {
        val feedUrl = "https://example.com/feed"
        assertEquals(
            "vault-1::${podcastImageCacheKey(feedUrl)}",
            podcastArtworkMemoryKey("vault-1", feedUrl)
        )
    }
}
