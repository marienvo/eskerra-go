package com.eskerra.go.core.podcast

import com.eskerra.go.core.usecase.MarkPodcastEpisodePlayed
import com.eskerra.go.core.usecase.markPodcastEpisodeAsPlayedInContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkPodcastEpisodePlayedTest {

    @Test
    fun `markPodcastEpisodeAsPlayedInContent flips first unchecked matching line`() {
        val content = """
            - [ ] 2026-03-15; One [▶](https://cdn/one.mp3) (News)
            - [ ] 2026-03-16; Two [▶](https://cdn/two.mp3) (News)
        """.trimIndent()

        val result = markPodcastEpisodeAsPlayedInContent(content, "https://cdn/two.mp3")

        assertTrue(result.updated)
        assertEquals(
            """
            - [ ] 2026-03-15; One [▶](https://cdn/one.mp3) (News)
            - [x] 2026-03-16; Two [▶](https://cdn/two.mp3) (News)
            """.trimIndent(),
            result.content
        )
    }

    @Test
    fun `markPodcastEpisodeAsPlayedInContent preserves leading whitespace and crlf`() {
        val content = "  - [ ] 2026-03-15; One [▶](https://cdn/one.mp3) (News)\r\nnext"

        val result = MarkPodcastEpisodePlayed().invoke(content, "https://cdn/one.mp3")

        assertTrue(result.updated)
        assertEquals(
            "  - [x] 2026-03-15; One [▶](https://cdn/one.mp3) (News)\r\nnext",
            result.content
        )
    }

    @Test
    fun `markPodcastEpisodeAsPlayedInContent skips when first matching line is already played`() {
        val content = """
            - [x] 2026-03-15; Played [▶](https://cdn/dup.mp3) (News)
            - [ ] 2026-03-16; Later [▶](https://cdn/dup.mp3) (News)
        """.trimIndent()

        val result = markPodcastEpisodeAsPlayedInContent(content, "https://cdn/dup.mp3")

        assertFalse(result.updated)
        assertEquals(content, result.content)
    }

    @Test
    fun `markPodcastEpisodeAsPlayedInContent skips when url is absent or empty`() {
        val content = "- [ ] 2026-03-15; One [▶](https://cdn/one.mp3) (News)"

        assertFalse(markPodcastEpisodeAsPlayedInContent(content, "https://cdn/missing.mp3").updated)
        assertFalse(markPodcastEpisodeAsPlayedInContent(content, "").updated)
    }
}
