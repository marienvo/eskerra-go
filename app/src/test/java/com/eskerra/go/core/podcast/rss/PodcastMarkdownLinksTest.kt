package com.eskerra.go.core.podcast.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastMarkdownLinksTest {

    @Test
    fun `normalizeTitleKey keeps lowercase ascii letters and digits only`() {
        assertEquals(
            "thesame123caf",
            PodcastMarkdownLinks.normalizeTitleKey("The Same! 123 — café")
        )
        assertEquals(
            PodcastMarkdownLinks.normalizeTitleKey("Hello, World"),
            PodcastMarkdownLinks.normalizeTitleKey("hello world")
        )
    }

    @Test
    fun `empty normalization falls back to a stable hash-keyed token`() {
        val key = PodcastMarkdownLinks.normalizeTitleKey("———")
        assertTrue(key.startsWith("_empty:"))
        assertEquals(key, PodcastMarkdownLinks.normalizeTitleKey("———"))
    }

    @Test
    fun `sanitizeUrl replaces amp entity and hasAmpEntity detects it`() {
        assertEquals("https://x?a=1&b=2", PodcastMarkdownLinks.sanitizeUrl("https://x?a=1&amp;b=2"))
        assertTrue(PodcastMarkdownLinks.hasAmpEntity("https://x?a=1&amp;b=2"))
        assertFalse(PodcastMarkdownLinks.hasAmpEntity("https://x?a=1&b=2"))
    }
}
