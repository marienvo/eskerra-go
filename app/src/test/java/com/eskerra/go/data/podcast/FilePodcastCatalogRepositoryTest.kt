package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePodcastCatalogRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val play = "\u25B6"

    @Test
    fun load_returnsEmptyCatalogWhenGeneralDirectoryMissing() = runTest {
        val filesDir = temp.newFolder("files")
        workspace(filesDir)

        val result = FilePodcastCatalogRepository(currentYear = { 2026 }).load(config, filesDir)

        assertTrue(result.isSuccess)
        assertEquals(FilePodcastCatalogRepository.emptyCatalog(), result.getOrThrow())
    }

    @Test
    fun load_parsesCurrentYearStubFilesAndHidesPlayedEpisodes() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        val generalDir = File(workspaceDir, FilePodcastCatalogRepository.GENERAL_DIRECTORY)
        generalDir.mkdirs()
        File(generalDir, "2026 News - podcasts.md").writeText(
            """
            - [ ] 2026-03-15; Fresh episode [$play](https://cdn/fresh.mp3) (Daily News)
            - [x] 2026-03-14; Played episode [$play](https://cdn/played.mp3) (Daily News)
            """.trimIndent()
        )
        File(generalDir, "2025 Old - podcasts.md").writeText(
            "- [ ] 2025-12-01; Old [$play](https://cdn/old.mp3) (Old Show)"
        )

        val catalog = FilePodcastCatalogRepository(currentYear = { 2026 })
            .load(config, filesDir)
            .getOrThrow()

        assertEquals(2, catalog.allEpisodes.size)
        assertEquals(1, catalog.sections.size)
        assertEquals("News", catalog.sections.single().title)
        assertEquals("Fresh episode", catalog.sections.single().episodes.single().title)
    }

    @Test
    fun load_enrichesSectionsAndEpisodesWithRssFeedUrlFromHub() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        val generalDir = File(workspaceDir, FilePodcastCatalogRepository.GENERAL_DIRECTORY)
        generalDir.mkdirs()
        File(generalDir, "2026 News - podcasts.md").writeText(
            "- [ ] 2026-03-15; Fresh episode [$play](https://cdn/fresh.mp3) (Daily News)\n"
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

        val catalog = FilePodcastCatalogRepository(currentYear = { 2026 })
            .load(config, filesDir)
            .getOrThrow()

        val feedUrl = "https://feed.example/rss.xml"
        assertEquals(feedUrl, catalog.sections.single().rssFeedUrl)
        assertEquals(feedUrl, catalog.sections.single().episodes.single().rssFeedUrl)
        assertEquals(feedUrl, catalog.allEpisodes.single().rssFeedUrl)
    }

    @Test
    fun load_assignsPerSeriesFeedUrlsWhenSectionBundlesMultipleShows() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        val generalDir = File(workspaceDir, FilePodcastCatalogRepository.GENERAL_DIRECTORY)
        generalDir.mkdirs()
        File(generalDir, "2026 News - podcasts.md").writeText(
            "- [ ] 2026-03-15; Morning [$play](https://cdn/daily.mp3) (Daily News)\n" +
                "- [ ] 2026-03-15; Weekly wrap [$play](https://cdn/weekly.mp3) (Weekly News)\n"
        )
        File(generalDir, "2026 News.md").writeText(
            "- [ ] [[📻 Daily News.md]]\n- [ ] [[📻 Weekly News.md]]\n"
        )
        File(generalDir, "📻 Daily News.md").writeText(
            "---\nrssFeedUrl: https://feed.example/daily.xml\n---\n# Daily News\n"
        )
        File(generalDir, "📻 Weekly News.md").writeText(
            "---\nrssFeedUrl: https://feed.example/weekly.xml\n---\n# Weekly News\n"
        )

        val catalog = FilePodcastCatalogRepository(currentYear = { 2026 })
            .load(config, filesDir)
            .getOrThrow()

        val byMp3 = catalog.allEpisodes.associateBy { it.mp3Url }
        assertEquals("https://feed.example/daily.xml", byMp3["https://cdn/daily.mp3"]?.rssFeedUrl)
        assertEquals("https://feed.example/weekly.xml", byMp3["https://cdn/weekly.mp3"]?.rssFeedUrl)
    }

    @Test
    fun load_deduplicatesSameMp3AcrossStubFiles() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = workspace(filesDir)
        val generalDir = File(workspaceDir, FilePodcastCatalogRepository.GENERAL_DIRECTORY)
        generalDir.mkdirs()
        val line = "- [ ] 2026-03-15; Shared [$play](https://cdn/shared.mp3) (Show)"
        File(generalDir, "2026 News - podcasts.md").writeText(line)
        File(generalDir, "2026 Work - podcasts.md").writeText(line)

        val catalog = FilePodcastCatalogRepository(currentYear = { 2026 })
            .load(config, filesDir)
            .getOrThrow()

        assertEquals(1, catalog.allEpisodes.size)
        assertEquals(1, catalog.sections.size)
    }

    private fun workspace(filesDir: File): File {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).apply { mkdirs() }
        return workspaceDir
    }
}
