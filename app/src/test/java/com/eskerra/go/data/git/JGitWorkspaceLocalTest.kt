package com.eskerra.go.data.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM tests for the local (non-remote) operations of [JGitWorkspaceRepository]:
 * init/open directory rules, status, path-safe writeFile, stage, and commit.
 */
class JGitWorkspaceLocalTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo = JGitWorkspaceRepository()

    @Test
    fun initOrOpen_initializesEmptyDirectory() {
        val dir = temp.newFolder("workspace")

        val result = repo.initOrOpen(dir)

        assertTrue(result.isSuccess)
        assertTrue("expected a .git directory", File(dir, ".git").isDirectory)
    }

    @Test
    fun initOrOpen_failsWhenDirectoryMissing() {
        val dir = File(temp.root, "missing-workspace")
        assertFalse(dir.exists())

        val result = repo.initOrOpen(dir)

        assertTrue(result.isFailure)
        assertFalse("must not create the directory", dir.exists())
    }

    @Test
    fun initOrOpen_opensExistingRepository() {
        val dir = temp.newFolder("workspace")
        assertTrue(repo.initOrOpen(dir).isSuccess)

        val reopened = repo.initOrOpen(dir)

        assertTrue(reopened.isSuccess)
    }

    @Test
    fun initOrOpen_failsForNonEmptyNonRepoDirectory() {
        val dir = temp.newFolder("not-a-repo")
        File(dir, "stray.txt").writeText("hello")

        val result = repo.initOrOpen(dir)

        assertTrue(result.isFailure)
    }

    @Test
    fun initOrOpen_failsWhenPathIsAFile() {
        val file = temp.newFile("regular.txt")

        val result = repo.initOrOpen(file)

        assertTrue(result.isFailure)
    }

    @Test
    fun status_reportsCleanThenDirtyThenCleanAcrossCommit() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val initialStatus = repo.status(dir).getOrThrow()
        assertFalse(initialStatus.hasUncommittedChanges)
        assertTrue(initialStatus.branch.isNotBlank())

        repo.writeFile(dir, "notes/first.md", "# First\n").getOrThrow()

        val dirtyStatus = repo.status(dir).getOrThrow()
        assertTrue(dirtyStatus.hasUncommittedChanges)
        assertTrue(dirtyStatus.changedPaths.contains("notes/first.md"))

        repo.stageAll(dir).getOrThrow()
        val commitId = repo.commit(dir, "Add first note").getOrThrow()
        assertTrue(commitId.isNotBlank())

        val finalStatus = repo.status(dir).getOrThrow()
        assertFalse(finalStatus.hasUncommittedChanges)
        assertTrue(finalStatus.changedPaths.isEmpty())
    }

    @Test
    fun stageAll_stagesPodcastRssCacheFilesWithEmojiNames() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val generalDir = File(dir, "General").apply { mkdirs() }
        val rssName = String(Character.toChars(0x1F4FB)) + " Daily News.md"
        File(generalDir, rssName).writeText("# RSS cache\n")
        File(generalDir, "2026 News - podcasts.md").writeText("- [ ] episode\n")

        val dirtyStatus = repo.status(dir).getOrThrow()
        assertEquals(2, dirtyStatus.changedPaths.size)

        repo.stageAll(dir).getOrThrow()
        val commitId = repo.commit(dir, "Stage podcast files").getOrThrow()
        assertTrue(commitId.isNotBlank())

        val finalStatus = repo.status(dir).getOrThrow()
        assertFalse(finalStatus.hasUncommittedChanges)
    }

    @Test
    fun writeFile_writesContentInsideWorkingDir() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        repo.writeFile(dir, "inbox/today.md", "body").getOrThrow()

        val written = File(dir, "inbox/today.md")
        assertTrue(written.isFile)
        assertEquals("body", written.readText())
        assertTrue(written.canonicalPath.startsWith(dir.canonicalPath))
    }

    @Test
    fun writeFile_rejectsAbsolutePath() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()
        val escapeTarget = File(temp.root, "escaped-absolute.md")

        val result = repo.writeFile(dir, escapeTarget.absolutePath, "nope")

        assertTrue(result.isFailure)
        assertFalse(escapeTarget.exists())
    }

    @Test
    fun writeFile_rejectsBlankPath() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        assertTrue(repo.writeFile(dir, "", "nope").isFailure)
        assertTrue(repo.writeFile(dir, "   ", "nope").isFailure)
    }

    @Test
    fun writeFile_rejectsParentTraversalSegments() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()
        val escapeTarget = File(temp.root, "escaped-traversal.md")

        val result = repo.writeFile(dir, "../escaped-traversal.md", "nope")

        assertTrue(result.isFailure)
        assertFalse(escapeTarget.exists())
    }

    @Test
    fun writeFile_rejectsNestedTraversalSegments() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()
        val escapeTarget = File(temp.root, "escaped-nested.md")

        val result = repo.writeFile(dir, "notes/../../escaped-nested.md", "nope")

        assertTrue(result.isFailure)
        assertFalse(escapeTarget.exists())
    }

    @Test
    fun writeFile_rejectsGitSegmentPaths() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()

        val blockedPaths = listOf(
            ".git/config",
            ".git/hooks/pre-commit",
            "notes/.git/config",
            "nested/.git/HEAD"
        )

        blockedPaths.forEach { relativePath ->
            val result = repo.writeFile(dir, relativePath, "nope")
            assertTrue("expected failure for $relativePath", result.isFailure)
        }

        assertFalse(File(dir, "notes/.git/config").exists())
        assertFalse(File(dir, "nested/.git/HEAD").exists())
    }
}
