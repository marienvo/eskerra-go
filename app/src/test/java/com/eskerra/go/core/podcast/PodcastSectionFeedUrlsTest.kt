package com.eskerra.go.core.podcast

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PodcastSectionFeedUrlsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val play = "\u25B6"

    @Test
    fun resolveSectionFeedUrls_mapsHubRssFrontmatterToSectionTitle() {
        val generalDir = temp.newFolder("general")
        File(generalDir, "2026 News - podcasts.md").writeText(
            "- [ ] 2026-03-15; Episode [$play](https://cdn/ep.mp3) (Daily News)\n"
        )
        File(generalDir, "2026 News.md").writeText(
            "- [ ] [[📻 Daily News.md]]\n"
        )
        File(generalDir, "📻 Daily News.md").writeText(
            """
            ---
            rssFeedUrl: https://feed.example/rss.xml
            ---
            # Daily News
            """.trimIndent()
        )

        val feedUrls = resolveSectionFeedUrls(generalDir, 2026)

        assertEquals(mapOf("News" to "https://feed.example/rss.xml"), feedUrls)
    }
}
