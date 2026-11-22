package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteContentRepository
import com.eskerra.go.data.notes.FileNoteRegistryRepository
import com.eskerra.go.data.notes.FileNoteWriteRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetDatedNoteReadStatusTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1L
    )
    private val noteId = NoteId("Work/Journal/2026-11-22.md")

    @Test
    fun writesLatestDiskContentAndEvictsCachedContent() = runTest {
        val filesDir = temp.newFolder("files")
        val workspace = gitWorkspace(filesDir)
        val noteFile = File(workspace, noteId.value)
        noteFile.parentFile!!.mkdirs()
        noteFile.writeText("# Today\n\nOriginal body")
        val contentCache = NoteContentCache(FileNoteContentRepository())
        contentCache.load(config, filesDir, noteId).getOrThrow()
        noteFile.writeText("# Today\n\nNewer external body")
        val useCase = SetDatedNoteReadStatus(
            repository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository()),
            contentCache = contentCache
        )

        val result = useCase(config, filesDir, noteId, read = true).getOrThrow()

        val expected = "# Today ✅\n\nNewer external body"
        assertEquals(expected, result.markdown)
        assertEquals(expected, noteFile.readText())
        assertEquals(expected, contentCache.load(config, filesDir, noteId).getOrThrow().markdown)
        assertEquals("Today ✅", result.registry.notes.single().title)
        assertFalse(result.registryRefreshFailed)
    }

    @Test
    fun rejectsNonDatedAndInboxPathsBeforeWriting() = runTest {
        val filesDir = temp.newFolder("files")
        gitWorkspace(filesDir)
        val useCase = SetDatedNoteReadStatus(
            repository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository())
        )

        listOf("Inbox/2026-11-22.md", "Work/Journal/Notes.md", "2026-11-22.md").forEach {
            val result = useCase(config, filesDir, NoteId(it), read = true)
            assertTrue("Expected failure for $it", result.isFailure)
        }
    }

    @Test
    fun missingTitleFailsWithoutChangingFile() = runTest {
        val filesDir = temp.newFolder("files")
        val workspace = gitWorkspace(filesDir)
        val noteFile = File(workspace, noteId.value)
        noteFile.parentFile!!.mkdirs()
        noteFile.writeText("Body only")
        val useCase = SetDatedNoteReadStatus(
            repository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryCache = NoteRegistryCache(FileNoteRegistryRepository())
        )

        val result = useCase(config, filesDir, noteId, read = true)

        assertTrue(result.isFailure)
        assertEquals("Body only", noteFile.readText())
    }

    @Test
    fun registryRefreshFailureKeepsSuccessfulLocalMutation() = runTest {
        val filesDir = temp.newFolder("files")
        val workspace = gitWorkspace(filesDir)
        val noteFile = File(workspace, noteId.value)
        noteFile.parentFile!!.mkdirs()
        noteFile.writeText("# Today")
        val registryRepository = FakeNoteRegistryRepository.withInboxNotes(
            NoteSummary(noteId, "Today", "", isInbox = false)
        )
        val registryCache = NoteRegistryCache(registryRepository)
        registryCache.refresh(config, filesDir).getOrThrow()
        registryRepository.setResult(Result.failure(Exception("refresh failed")))
        val useCase = SetDatedNoteReadStatus(
            repository = FileNoteWriteRepository(JGitWorkspaceRepository()),
            registryCache = registryCache
        )

        val result = useCase(config, filesDir, noteId, read = true).getOrThrow()

        assertTrue(result.registryRefreshFailed)
        assertEquals("# Today ✅", noteFile.readText())
        assertEquals("Today ✅", result.registry.notes.single().title)
    }

    private fun gitWorkspace(filesDir: File): File {
        val dir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        dir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(dir).getOrThrow()
        return dir
    }
}
