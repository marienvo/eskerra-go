package com.eskerra.go.data.git

import com.eskerra.go.data.notes.MarkdownNoteScanner
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitIndexInspectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gitRepo = JGitWorkspaceRepository()

    @Test
    fun readStagedPaths_includesStagedInboxFile() {
        val workspace = initWorkspace()
        gitRepo.writeFile(workspace, "Inbox/staged.md", "# Staged\n").getOrThrow()
        Git.open(workspace).use { git ->
            git.add().addFilepattern("Inbox/staged.md").call()
        }

        val paths = GitIndexInspector.readStagedPaths(workspace).getOrThrow()

        assertTrue(paths.any { it.contains("Inbox/staged.md") })
    }

    @Test
    fun readStagedPaths_includesStagedNonInboxFile() {
        val workspace = initWorkspace()
        gitRepo.writeFile(workspace, "Projects/staged.md", "# Staged\n").getOrThrow()
        Git.open(workspace).use { git ->
            git.add().addFilepattern("Projects/staged.md").call()
        }

        val paths = GitIndexInspector.readStagedPaths(workspace).getOrThrow()

        assertTrue(paths.any { it.contains("Projects/staged.md") })
    }

    private fun initWorkspace(): File {
        val bare = TestGitRepos.initBareRemote(File(temp.root, "remote.git"))
        val filesDir = temp.newFolder("files")
        val workspace = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        gitRepo.cloneFrom(TestGitRepos.fileUri(bare), workspace).getOrThrow()
        gitRepo.writeFile(workspace, "${MarkdownNoteScanner.INBOX_DIRECTORY}/seed.md", "# Seed\n")
            .getOrThrow()
        gitRepo.stageAll(workspace).getOrThrow()
        gitRepo.commit(workspace, "Seed").getOrThrow()
        return workspace
    }
}
