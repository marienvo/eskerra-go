package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.GitStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.git.StatusFailingGitRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadGitStatusSummaryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun cleanWorkspaceMapsToClean() {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        val useCase = LoadGitStatusSummary(gitRepository)

        val status = useCase(config, filesDir)

        assertEquals(GitStatusSummary.State.Clean, status.state)
        assertFalse(status.changedCount > 0)
        assertTrue(!workspaceDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun dirtyWorkspaceMapsToDirtyWithChangedCount() {
        val filesDir = temp.newFolder("files")
        val workspaceDir = gitWorkspace(filesDir)
        val gitRepository = JGitWorkspaceRepository()
        gitRepository.writeFile(workspaceDir, "Inbox/new.md", "# New").getOrThrow()
        val useCase = LoadGitStatusSummary(gitRepository)

        val status = useCase(config, filesDir)

        assertEquals(GitStatusSummary.State.Dirty, status.state)
        assertTrue(status.changedCount >= 1)
    }

    @Test
    fun missingWorkspaceMapsToUnavailable() {
        val filesDir = temp.newFolder("files")
        val useCase = LoadGitStatusSummary(JGitWorkspaceRepository())

        val status = useCase(config, filesDir)

        assertEquals(GitStatusSummary.State.Unavailable, status.state)
    }

    @Test
    fun nonGitDirectoryMapsToUnavailable() {
        val filesDir = temp.newFolder("files")
        File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).mkdirs()
        val useCase = LoadGitStatusSummary(JGitWorkspaceRepository())

        val status = useCase(config, filesDir)

        assertEquals(GitStatusSummary.State.Unavailable, status.state)
    }

    @Test
    fun statusFailureMapsToError() {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val useCase = LoadGitStatusSummary(StatusFailingGitRepository())

        val status = useCase(config, filesDir)

        assertEquals(GitStatusSummary.State.Error, status.state)
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
