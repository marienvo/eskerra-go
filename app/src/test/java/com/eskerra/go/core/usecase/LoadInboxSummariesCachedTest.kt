package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeInboxSnapshotStore
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadInboxSummariesCachedTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "master",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    private val inboxNote = NoteSummary(
        id = NoteId("Inbox/hello.md"),
        title = "Hello",
        snippet = "Body",
        isInbox = true
    )

    @Test
    fun readCached_returnsSnapshotWithoutScanning() = runTest {
        val filesDir = temp.newFolder("files")
        val snapshotStore = FakeInboxSnapshotStore()
        snapshotStore.save(config, filesDir, listOf(inboxNote))
        val repository = FakeNoteRegistryRepository.withInboxNotes(inboxNote)
        val useCase = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(NoteRegistryCache(repository)),
            snapshotStore = snapshotStore
        )

        assertEquals(listOf(inboxNote), useCase.readCached(config, filesDir))
        assertEquals(0, repository.refreshCount)
    }

    @Test
    fun invoke_persistsInboxSummariesOnSuccess() = runTest {
        val filesDir = temp.newFolder("files")
        val snapshotStore = FakeInboxSnapshotStore()
        val repository = FakeNoteRegistryRepository.withInboxNotes(inboxNote)
        val useCase = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(NoteRegistryCache(repository)),
            snapshotStore = snapshotStore
        )

        val result = useCase(config, filesDir)

        assertEquals(listOf(inboxNote), result.getOrThrow())
        assertEquals(listOf(inboxNote), snapshotStore.read(config, filesDir))
    }

    @Test
    fun invoke_doesNotPersistOnFailure() = runTest {
        val filesDir = temp.newFolder("files")
        val snapshotStore = FakeInboxSnapshotStore()
        val repository = FakeNoteRegistryRepository.failing()
        val useCase = LoadInboxSummariesCached(
            delegate = LoadInboxSummaries(NoteRegistryCache(repository)),
            snapshotStore = snapshotStore
        )

        val result = useCase(config, filesDir)

        assertTrue(result.isFailure)
        assertNull(snapshotStore.read(config, filesDir))
    }
}
