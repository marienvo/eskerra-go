package com.eskerra.go.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspacePathsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun validateRelativePath_rejectsBlank() {
        assertTrue(WorkspacePaths.validateRelativePath("").isFailure)
        assertTrue(WorkspacePaths.validateRelativePath("   ").isFailure)
    }

    @Test
    fun validateRelativePath_rejectsAbsolute() {
        assertTrue(WorkspacePaths.validateRelativePath("/absolute/path").isFailure)
    }

    @Test
    fun validateRelativePath_rejectsParentTraversal() {
        assertTrue(WorkspacePaths.validateRelativePath("../escape").isFailure)
        assertTrue(WorkspacePaths.validateRelativePath("workspace/../escape").isFailure)
    }

    @Test
    fun validateRelativePath_acceptsDefaultPath() {
        assertTrue(WorkspacePaths.validateRelativePath(WorkspacePaths.DEFAULT_RELATIVE_PATH).isSuccess)
    }

    @Test
    fun resolve_staysInsideFilesDir() {
        val filesDir = temp.newFolder("files")
        val result = WorkspacePaths.resolve(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)

        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertTrue(WorkspacePaths.isInsideFilesDir(filesDir, resolved))
        assertEquals(File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH).canonicalFile, resolved)
    }

    @Test
    fun resolve_rejectsUnsafeRelativePath() {
        val filesDir = temp.newFolder("files")
        val result = WorkspacePaths.resolve(filesDir, "../outside")

        assertTrue(result.isFailure)
    }

    @Test
    fun isValidGitWorkspace_requiresDotGitDirectory() {
        val dir = temp.newFolder("workspace")
        assertTrue(!WorkspacePaths.isValidGitWorkspace(dir))

        File(dir, ".git").mkdir()
        assertTrue(WorkspacePaths.isValidGitWorkspace(dir))
    }
}
