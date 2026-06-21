package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePodcastCatalogSnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun saveAndRead_roundTripsCatalogForSameWorkspace() = runTest {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val episode = sampleEpisode()
        val catalog = PodcastCatalog(
            allEpisodes = listOf(episode),
            sections = listOf(
                PodcastSection(title = "News", rssFeedUrl = null, episodes = listOf(episode))
            )
        )
        val store = FilePodcastCatalogSnapshotStore()

        store.save(config, filesDir, catalog)
        val restored = store.read(config, filesDir)

        assertEquals(catalog, restored)
    }

    @Test
    fun read_returnsNullWhenWorkspaceFingerprintDiffers() = runTest {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val episode = sampleEpisode()
        val catalog = PodcastCatalog(
            allEpisodes = listOf(episode),
            sections = listOf(
                PodcastSection(title = "News", rssFeedUrl = null, episodes = listOf(episode))
            )
        )
        val store = FilePodcastCatalogSnapshotStore()
        store.save(config, filesDir, catalog)

        val otherConfig = config.copy(branch = "other")
        assertNull(store.read(otherConfig, filesDir))
    }

    @Test
    fun clear_removesSnapshotForCurrentWorkspace() = runTest {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val episode = sampleEpisode()
        val catalog = PodcastCatalog(
            allEpisodes = listOf(episode),
            sections = listOf(
                PodcastSection(title = "News", rssFeedUrl = null, episodes = listOf(episode))
            )
        )
        val store = FilePodcastCatalogSnapshotStore()
        store.save(config, filesDir, catalog)

        store.clear(config, filesDir)

        assertNull(store.read(config, filesDir))
        assertTrue(
            !File(filesDir, "cache/podcast_catalog_snapshot.json").isFile ||
                store.read(config, filesDir) == null
        )
    }

    private fun sampleEpisode() = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = "episode-1",
        isListened = false,
        mp3Url = "https://cdn/episode.mp3",
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily",
        sourceFile = "General/2026 News - podcasts.md",
        title = "Episode title"
    )
}
