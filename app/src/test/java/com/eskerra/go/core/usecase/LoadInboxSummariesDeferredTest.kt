package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.FakeInboxSnapshotStore
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadInboxSummariesDeferredTest {
    @Test
    fun doesNotScanUntilLaunchIsSettled() = runTest {
        val repository = FakeNoteRegistryRepository()
        val loader = LoadInboxSummariesCached(
            LoadInboxSummaries(NoteRegistryCache(repository)),
            FakeInboxSnapshotStore()
        )
        loader.deferUntilLaunchSettled()
        val pending = async { loader(WorkspaceConfig("Test", "vault", null, "main", 1), File(".")) }
        runCurrent()
        assertEquals(0, repository.refreshCount)
        assertTrue(loader.isLaunchDeferred)
        loader.onLaunchSettled()
        runCurrent()
        assertTrue(pending.await().isSuccess)
        assertEquals(1, repository.refreshCount)
        loader.deferUntilLaunchSettled()
        assertTrue(!loader.isLaunchDeferred)
    }
}
