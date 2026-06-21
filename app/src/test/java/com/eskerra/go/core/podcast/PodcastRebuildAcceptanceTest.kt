package com.eskerra.go.core.podcast

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.LoadPodcastCatalog
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayed
import com.eskerra.go.core.usecase.SyncPodcastChange
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.podcast.FilePodcastCatalogRepository
import com.eskerra.go.data.podcast.FilePodcastFileRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * End-to-end parity scenarios from spec §1 (Episodes tab). Maps each automated row to
 * chained real repositories/use cases; device-only paths (Media3 notification, Coil
 * render) stay manual — see class KDoc on [PodcastRebuildAcceptanceTest].
 */
internal object PodcastAcceptanceFixtures {
    const val PLAY = "\u25B6"

    fun workspaceConfig(filesDir: File) = WorkspaceConfig(
        name = "Vault",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    fun generalDir(filesDir: File): File {
        val dir = File(File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH), "General")
        dir.mkdirs()
        return dir
    }

    fun stubLine(title: String, mp3Url: String, series: String = "Daily") =
        "- [ ] 2026-03-15; $title [$PLAY]($mp3Url) ($series)\n"

    fun episodeFromStub(
        mp3Url: String,
        sourceFile: String = "2026 News - podcasts.md",
        title: String = "Episode title"
    ) = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = mp3Url,
        isListened = false,
        mp3Url = mp3Url,
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily",
        sourceFile = sourceFile,
        title = title
    )
}

/**
 * Automated acceptance for the Kotlin podcasts rebuild (plan phase 9).
 *
 * Manual device checklist (not automated here):
 * - Mini player visible on all tabs while playing
 * - Foreground notification + lock-screen transport while app backgrounded
 * - Pull-to-refresh strip on Episodes tab
 * - R2 playlist handoff between two devices (requires R2 config)
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PodcastRebuildAcceptanceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()

    @Test
    fun catalog_loadsUnplayedStubEpisodesWithSectionGrouping() = runTest {
        val filesDir = temp.newFolder("files")
        val general = PodcastAcceptanceFixtures.generalDir(filesDir)
        File(general, "2026 News - podcasts.md").writeText(
            PodcastAcceptanceFixtures.stubLine("Fresh", "https://cdn/fresh.mp3")
        )
        File(general, "2026 News - podcasts.md").appendText(
            "- [x] 2026-03-14; Played [${PodcastAcceptanceFixtures.PLAY}](https://cdn/old.mp3) (Daily)\n"
        )

        val catalog = LoadPodcastCatalog(FilePodcastCatalogRepository(currentYear = { 2026 }))(
            PodcastAcceptanceFixtures.workspaceConfig(filesDir),
            filesDir
        ).getOrThrow()

        assertEquals(2, catalog.allEpisodes.size)
        assertEquals(1, catalog.sections.single().episodes.size)
        assertEquals("Fresh", catalog.sections.single().episodes.single().title)
        assertEquals("News", catalog.sections.single().title)
    }

    @Test
    fun markAsPlayed_localWriteThenReloadHidesEpisode() = runTest {
        val filesDir = temp.newFolder("files")
        val general = PodcastAcceptanceFixtures.generalDir(filesDir)
        val stubPath = "2026 News - podcasts.md"
        File(general, stubPath).writeText(
            PodcastAcceptanceFixtures.stubLine("Only", "https://cdn/only.mp3")
        )
        val config = PodcastAcceptanceFixtures.workspaceConfig(filesDir)
        val loadCatalog = LoadPodcastCatalog(FilePodcastCatalogRepository(currentYear = { 2026 }))
        val episode = PodcastAcceptanceFixtures.episodeFromStub(
            mp3Url = "https://cdn/only.mp3",
            sourceFile = stubPath,
            title = "Only"
        )

        assertEquals(1, loadCatalog(config, filesDir).getOrThrow().allEpisodes.size)

        MarkPodcastEpisodesPlayed(
            podcastFileRepository = FilePodcastFileRepository(),
            syncPodcastChange = { _, _ -> Result.success(PodcastSyncResult.NOTHING_TO_COMMIT) }
        )(config, filesDir, listOf(episode)).getOrThrow()

        val reloaded = loadCatalog(config, filesDir).getOrThrow()
        assertTrue(reloaded.sections.isEmpty())
        assertTrue(reloaded.allEpisodes.single().isListened)
        assertTrue(File(general, stubPath).readText().contains("- [x]"))
    }

    @Test
    fun markAsPlayed_gitCommitRemovesEpisodeFromSections() = runTest {
        val setup = seedGitWorkspace(
            "General/2026 News - podcasts.md" to
                PodcastAcceptanceFixtures.stubLine("First", "https://cdn/ep1.mp3")
        )
        val loadCatalog = LoadPodcastCatalog(FilePodcastCatalogRepository(currentYear = { 2026 }))
        val markPlayed = MarkPodcastEpisodesPlayed(
            podcastFileRepository = FilePodcastFileRepository(),
            syncPodcastChange = SyncPodcastChange(
                remoteSyncRepository = remoteSync,
                credentialStore = credentials,
                gitSyncMutex = GitSyncMutex()
            )::invoke
        )
        val episode = PodcastAcceptanceFixtures.episodeFromStub(
            mp3Url = "https://cdn/ep1.mp3",
            sourceFile = "2026 News - podcasts.md",
            title = "First"
        )

        val beforeMark = loadCatalog(setup.config, setup.filesDir).getOrThrow()
        assertEquals(1, beforeMark.sections.single().episodes.size)

        val result = markPlayed(setup.config, setup.filesDir, listOf(episode)).getOrThrow()

        assertTrue(result.updated)
        assertTrue(result.sync.committed)
        assertTrue(result.sync.pushed)
        assertTrue(loadCatalog(setup.config, setup.filesDir).getOrThrow().sections.isEmpty())

        val verify = temp.newFolder("verify")
        gitRepo.cloneFrom(setup.remoteUri, verify).getOrThrow()
        assertTrue(
            File(verify, "General/2026 News - podcasts.md").readText().contains("- [x]")
        )
    }

    private data class GitWorkspaceSetup(
        val filesDir: File,
        val config: WorkspaceConfig,
        val remoteUri: String
    )

    private fun seedGitWorkspace(vararg files: Pair<String, String>): GitWorkspaceSetup {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val remoteUri = TestGitRepos.fileUri(bare)
        val producer = temp.newFolder("seed")
        gitRepo.cloneFrom(remoteUri, producer).getOrThrow()
        files.forEach { (path, content) ->
            gitRepo.writeFile(producer, path, content).getOrThrow()
        }
        gitRepo.stageAll(producer).getOrThrow()
        gitRepo.commit(producer, "Seed").getOrThrow()
        gitRepo.push(producer).getOrThrow()
        val branch = gitRepo.status(producer).getOrThrow().branch

        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(remoteUri, workspaceDir).getOrThrow()

        val config = WorkspaceConfig(
            name = "Test Workspace",
            relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
            remoteUri = remoteUri,
            branch = branch,
            setupCompletedAtEpochMs = 0L
        )
        return GitWorkspaceSetup(filesDir, config, remoteUri)
    }
}
