package com.eskerra.go.data.podcast.rss

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastRefreshProgress
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePodcastRssVaultSyncTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-03-15T12:00:00Z").toEpochMilli()
    private val play = "▶"

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private fun feed(title: String, url: String, pubDate: String) = """
        <rss><channel><item>
          <title>$title</title>
          <pubDate>$pubDate</pubDate>
          <enclosure url="$url" type="audio/mpeg"/>
        </item></channel></rss>
    """.trimIndent()

    @Test
    fun refresh_refreshesIncludedFeedMergesStubAndReportsProgress() = runTest {
        val filesDir = temp.newFolder("files")
        val generalDir = File(File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH), "General")
        generalDir.mkdirs()

        File(generalDir, "2026 News - podcasts.md").writeText(
            "- [ ] 2026-03-15; Existing [$play](https://cdn/existing.mp3) (Daily News)\n"
        )
        File(generalDir, "2026 News.md").writeText(
            """
            - [ ] [[📻 Daily News.md]]
            - [x] [[📻 Excluded.md]]
            """.trimIndent()
        )
        File(generalDir, "📻 Daily News.md").writeText(
            """
            ---
            rssFeedUrl: https://feed/news
            ---
            # Daily News
            """.trimIndent()
        )
        val excludedOriginal = """
            ---
            rssFeedUrl: https://feed/excluded
            ---
            # Excluded
        """.trimIndent()
        File(generalDir, "📻 Excluded.md").writeText(excludedOriginal)

        val fetcher = RssFeedFetcher { url, _ ->
            if (url == "https://feed/news") {
                feed("Fresh story", "https://cdn/fresh.mp3", "Sun, 15 Mar 2026 09:00:00 +0000")
            } else {
                null
            }
        }
        val progress = mutableListOf<PodcastRefreshProgress>()

        val summary = FilePodcastRssVaultSync(
            fetcher = fetcher,
            currentYear = { 2026 },
            nowMs = { now },
            zoneId = zone
        ).refresh(config, filesDir) { progress += it }.getOrThrow()

        // Included 📻 refreshed.
        val rssBody = File(generalDir, "📻 Daily News.md").readText()
        assertTrue(rssBody.contains("rssFetchedAt:"))
        assertTrue(rssBody.contains("https://cdn/fresh.mp3"))

        // Stub merged with the fresh candidate, existing retained.
        val stub = File(generalDir, "2026 News - podcasts.md").readText()
        assertTrue(stub.contains("https://cdn/fresh.mp3"))
        assertTrue(stub.contains("https://cdn/existing.mp3"))

        // Excluded 📻 untouched.
        assertEquals(excludedOriginal, File(generalDir, "📻 Excluded.md").readText())

        assertEquals(1, summary.refreshedFileCount)
        assertEquals(1, summary.mergedStubCount)
        assertEquals(PodcastRefreshProgress.PHASE_COMPLETE, progress.last().phase)
        assertEquals(100, progress.last().percent)
    }

    @Test
    fun refresh_withoutGeneralDirectoryCompletesEmpty() = runTest {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val progress = mutableListOf<PodcastRefreshProgress>()

        val summary = FilePodcastRssVaultSync(
            fetcher = { _, _ -> null },
            currentYear = { 2026 },
            nowMs = { now },
            zoneId = zone
        ).refresh(config, filesDir) { progress += it }.getOrThrow()

        assertEquals(0, summary.refreshedFileCount)
        assertEquals(0, summary.mergedStubCount)
        assertFalse(progress.isEmpty())
        assertEquals(PodcastRefreshProgress.PHASE_COMPLETE, progress.last().phase)
    }
}
