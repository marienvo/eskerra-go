package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
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

class FileInboxSnapshotStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val note = NoteSummary(
        id = NoteId("Inbox/hello.md"),
        title = "Hello",
        snippet = "Body",
        isInbox = true,
        lastModifiedEpochMillis = 42L
    )

    @Test
    fun saveAndRead_roundTripsSummaries() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val store = FileInboxSnapshotStore()

        store.save(config, filesDir, listOf(note))

        assertEquals(listOf(note), store.read(config, filesDir))
    }

    @Test
    fun saveAndRead_roundTripsEmptySummaries() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val store = FileInboxSnapshotStore()

        store.save(config, filesDir, emptyList<NoteSummary>())

        assertEquals(emptyList<NoteSummary>(), store.read(config, filesDir))
    }

    @Test
    fun read_returnsNullForCorruptSnapshot() = runTest {
        val filesDir = temp.newFolder("files")
        prepareWorkspace(filesDir)
        val snapshotFile = File(filesDir, "cache/inbox_snapshot.json")
        snapshotFile.parentFile?.mkdirs()
        snapshotFile.writeText("{not-json")

        val store = FileInboxSnapshotStore()

        assertNull(store.read(config, filesDir))
    }

    private fun prepareWorkspace(filesDir: File) {
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        JGitWorkspaceRepository().initOrOpen(workspaceDir).getOrThrow()
    }
}
