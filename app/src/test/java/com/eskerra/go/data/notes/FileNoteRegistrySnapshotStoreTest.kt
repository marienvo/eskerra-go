package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.JGitWorkspaceRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileNoteRegistrySnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val inboxNote = NoteSummary(
        id = NoteId("Inbox/hello.md"),
        title = "Hello",
        snippet = "Body",
        isInbox = true,
        lastModifiedEpochMillis = 42L
    )

    private val vaultNote = NoteSummary(
        id = NoteId("Notes/other.md"),
        title = "Other",
        snippet = "More",
        isInbox = false,
        lastModifiedEpochMillis = 43L
    )

    @Test
    fun saveAndRead_roundTripsRegistry() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val store = FileNoteRegistrySnapshotStore()
        val registry = NoteRegistry.fromNotes(listOf(inboxNote, vaultNote))

        store.save(config, filesDir, registry)

        assertEquals(registry, store.read(config, filesDir))
    }

    @Test
    fun saveAndRead_roundTripsEmptyRegistry() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val store = FileNoteRegistrySnapshotStore()
        val registry = NoteRegistry.fromNotes(emptyList())

        store.save(config, filesDir, registry)

        assertEquals(registry, store.read(config, filesDir))
    }

    @Test
    fun read_returnsNullForCorruptSnapshot() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val snapshotFile = File(
            filesDir,
            "cache/${FileNoteRegistrySnapshotStore.SNAPSHOT_FILE_NAME}"
        )
        snapshotFile.parentFile?.mkdirs()
        snapshotFile.writeText("{not-json")

        val store = FileNoteRegistrySnapshotStore()

        assertNull(store.read(config, filesDir))
    }

    private fun prepareWorkspace(filesDir: File) {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }
}
