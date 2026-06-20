package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.credentials.FakeCredentialStore
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.TestGitRepos
import com.eskerra.go.data.podcast.FilePodcastFileRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarkPodcastEpisodesPlayedTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()
    private val remoteSync = JGitRemoteSyncRepository(gitRepo)
    private val credentials = FakeCredentialStore()

    private fun useCase() = MarkPodcastEpisodesPlayed(
        podcastFileRepository = FilePodcastFileRepository(),
        syncPodcastChange = SyncPodcastChange(
            remoteSyncRepository = remoteSync,
            credentialStore = credentials,
            gitSyncMutex = GitSyncMutex()
        )::invoke
    )

    @Test
    fun mark_flipsCheckboxCommitsAndPushes() = runTest {
        val setup = seedWorkspace(
            "General/2026 News - podcasts.md" to NEWS_FILE
        )

        val result = useCase()(
            setup.config,
            setup.filesDir,
            listOf(episode("https://cdn/ep1.mp3", "2026 News - podcasts.md"))
        ).getOrThrow()

        assertTrue(result.updated)
        assertEquals(listOf("General/2026 News - podcasts.md"), result.updatedPaths)
        assertTrue(result.sync.committed)
        assertTrue(result.sync.pushed)

        val content = File(setup.workspaceDir, "General/2026 News - podcasts.md").readText()
        assertTrue(content.contains("- [x] 2026-03-15; First"))
        assertTrue(content.contains("- [ ] 2026-03-16; Second"))
    }

    @Test
    fun alreadyPlayed_isNoOp() = runTest {
        val setup = seedWorkspace(
            "General/2026 News - podcasts.md" to NEWS_FILE
        )
        val played = "https://cdn/ep1.mp3"
        useCase()(
            setup.config,
            setup.filesDir,
            listOf(episode(played, "2026 News - podcasts.md"))
        ).getOrThrow()

        val again = useCase()(
            setup.config,
            setup.filesDir,
            listOf(episode(played, "2026 News - podcasts.md"))
        ).getOrThrow()

        assertFalse(again.updated)
        assertTrue(again.updatedPaths.isEmpty())
        assertFalse(again.sync.committed)
    }

    @Test
    fun batchAcrossFiles_producesSingleCommit() = runTest {
        val setup = seedWorkspace(
            "General/2026 News - podcasts.md" to NEWS_FILE,
            "General/2026 Tech - podcasts.md" to TECH_FILE
        )

        val result = useCase()(
            setup.config,
            setup.filesDir,
            listOf(
                episode("https://cdn/ep1.mp3", "2026 News - podcasts.md"),
                episode("https://cdn/tech1.mp3", "2026 Tech - podcasts.md")
            )
        ).getOrThrow()

        assertTrue(result.updated)
        assertEquals(2, result.updatedPaths.size)
        assertTrue(result.sync.committed)

        val log = org.eclipse.jgit.api.Git.open(setup.workspaceDir).use { git ->
            git.log().call().toList()
        }
        val podcastCommits = log.count {
            it.fullMessage.contains(SyncPodcastChange.PODCAST_COMMIT_MESSAGE)
        }
        assertEquals(1, podcastCommits)
    }

    @Test
    fun unknownEpisode_doesNotCommit() = runTest {
        val setup = seedWorkspace(
            "General/2026 News - podcasts.md" to NEWS_FILE
        )

        val result = useCase()(
            setup.config,
            setup.filesDir,
            listOf(episode("https://cdn/missing.mp3", "2026 News - podcasts.md"))
        ).getOrThrow()

        assertFalse(result.updated)
        assertFalse(result.sync.committed)
    }

    private fun episode(mp3Url: String, sourceFile: String) = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = mp3Url,
        isListened = false,
        mp3Url = mp3Url,
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily",
        sourceFile = sourceFile,
        title = "Title"
    )

    private data class WorkspaceSetup(
        val filesDir: File,
        val workspaceDir: File,
        val config: WorkspaceConfig
    )

    private fun seedWorkspace(vararg files: Pair<String, String>): WorkspaceSetup {
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
        return WorkspaceSetup(filesDir, workspaceDir, config)
    }

    private companion object {
        val NEWS_FILE = "- [ ] 2026-03-15; First [▶](https://cdn/ep1.mp3) (Daily)\n" +
            "- [ ] 2026-03-16; Second [▶](https://cdn/ep2.mp3) (Daily)\n"
        val TECH_FILE = "- [ ] 2026-03-15; Tech [▶](https://cdn/tech1.mp3) (Weekly)\n"
    }
}
