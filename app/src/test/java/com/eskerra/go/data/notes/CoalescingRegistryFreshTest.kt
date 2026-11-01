package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescingRegistryFreshTest {
    @Test
    fun freshScanDoesNotReuseOneThatStartedBeforeTheWrite() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var count = 0
        val delegate = object : NoteRegistryRepository {
            override suspend fun refresh(
                config: WorkspaceConfig,
                filesDir: File,
                previousRegistry: NoteRegistry?
            ): Result<NoteRegistry> {
                count++
                if (count == 1) {
                    started.complete(Unit)
                    release.await()
                }
                return Result.success(NoteRegistry(emptyList()))
            }
        }
        val repository = CoalescingNoteRegistryRepository(delegate)
        val config = WorkspaceConfig("Test", "vault", null, "main", 1)
        val first = async { repository.refresh(config, File(".")) }
        started.await()
        val fresh = async { repository.refreshFresh(config, File("."), NoteRegistry(emptyList())) }
        runCurrent()
        assertEquals(1, count)
        release.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertTrue(fresh.await().isSuccess)
        assertEquals(2, count)
    }
}
