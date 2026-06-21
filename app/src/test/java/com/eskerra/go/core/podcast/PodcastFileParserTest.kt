package com.eskerra.go.core.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastFileParserTest {

    private val play = "\u25B6"

    private val details = PodcastFileDetails(
        fileName = "2026 News - podcasts.md",
        sectionTitle = "News",
        year = 2026
    )

    @Test
    fun `parsePodcastFileDetails accepts current and next year stubs`() {
        assertEquals(
            PodcastFileDetails("2026 News - podcasts.md", "News", 2026),
            parsePodcastFileDetails("General/2026 News - podcasts.md", currentYear = 2026)
        )
        assertEquals(
            PodcastFileDetails("2027 Work - PODCASTS.md", "Work", 2027),
            parsePodcastFileDetails("2027 Work - PODCASTS.md", currentYear = 2026)
        )
    }

    @Test
    fun `parsePodcastFileDetails rejects past and later years`() {
        assertFalse(isPodcastEpisodesFile("2025 News - podcasts.md", currentYear = 2026))
        assertFalse(isPodcastEpisodesFile("2028 News - podcasts.md", currentYear = 2026))
        assertNull(parsePodcastFileDetails("2026 News.md", currentYear = 2026))
    }

    @Test
    fun `parsePodcastEpisodeLine reads checkbox date title play link series and source`() {
        val line = "- [ ] 2026-03-15; Episode title [\u25B6](https://cdn/episode.mp3) (Series Name)"
        val episode = requireNotNull(parsePodcastEpisodeLine(line, details))

        assertEquals("https://cdn/episode.mp3", episode.id)
        assertEquals("https://cdn/episode.mp3", episode.mp3Url)
        assertEquals("2026-03-15", episode.date)
        assertEquals("Episode title", episode.title)
        assertEquals("Series Name", episode.seriesName)
        assertEquals("News", episode.sectionTitle)
        assertEquals("2026 News - podcasts.md", episode.sourceFile)
        assertFalse(episode.isListened)
    }

    @Test
    fun `parsePodcastEpisodeLine reads optional article link and listened state`() {
        val line = "- [x] 2026-03-14; [\uD83C\uDF10](https://article) Title " +
            "[\u25B6\uFE0F](https://cdn/episode.mp3) (Series)"
        val episode = requireNotNull(parsePodcastEpisodeLine(line, details))

        assertTrue(episode.isListened)
        assertEquals("https://article", episode.articleUrl)
        assertEquals("Title", episode.title)
    }

    @Test
    fun `parsePodcastEpisodeLine uses last play triangle link`() {
        val line = "- [ ] 2026-03-15; Title [\u25B6](https://cdn/preview.mp3) " +
            "[\u25B6](https://cdn/full.mp3) (Series)"
        val episode = requireNotNull(parsePodcastEpisodeLine(line, details))

        assertEquals("https://cdn/full.mp3", episode.mp3Url)
        assertEquals("Title [\u25B6](https://cdn/preview.mp3)", episode.title)
    }

    @Test
    fun `parsePodcastEpisodeLine rejects nested parens in series tail`() {
        val line = "- [ ] 2026-03-15; Title [\u25B6](https://cdn/episode.mp3) (Series (Inner))"

        assertNull(parsePodcastEpisodeLine(line, details))
    }

    @Test
    fun `parsePodcastFiles dedups by mp3 url with first file winning and sorts descending`() {
        val result = parsePodcastFiles(
            files = listOf(
                PodcastMarkdownFile(
                    "2026 News - podcasts.md",
                    """
                    - [ ] 2026-03-14; Older [$play](https://cdn/a.mp3) (News)
                    - [ ] 2026-03-16; First [$play](https://cdn/dup.mp3) (News)
                    """.trimIndent()
                ),
                PodcastMarkdownFile(
                    "2026 Work - podcasts.md",
                    """
                    - [ ] 2026-03-17; Duplicate [$play](https://cdn/dup.mp3) (Work)
                    - [ ] 2026-03-15; Middle [$play](https://cdn/b.mp3) (Work)
                    """.trimIndent()
                )
            ),
            currentYear = 2026
        )

        assertEquals(listOf("First", "Middle", "Older"), result.allEpisodes.map { it.title })
        assertEquals("News", result.allEpisodes.first().sectionTitle)
    }
}
