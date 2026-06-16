package com.eskerra.go.data.notes

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoalescingNoteRegistryRepositoryTest {

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
    fun refresh_coalescesConcurrentCallsForSameWorkspace() = runTest {
        val filesDir = temp.newFolder("files")
        val delegate = FakeNoteRegistryRepository(refreshDelayMs = 200)
        val repository = CoalescingNoteRegistryRepository(delegate)

        coroutineScope {
            val first = async { repository.refresh(config, filesDir) }
            delay(50)
            val second = async { repository.refresh(config, filesDir) }
            assertEquals(first.await(), second.await())
        }

        assertEquals(1, delegate.refreshCount)
    }

    @Test
    fun refresh_sequentialCallsEachScanAgain() = runTest {
        val filesDir = temp.newFolder("files")
        val delegate = FakeNoteRegistryRepository()
        val repository = CoalescingNoteRegistryRepository(delegate)

        repository.refresh(config, filesDir)
        repository.refresh(config, filesDir)

        assertEquals(2, delegate.refreshCount)
    }

    @Test
    fun refresh_delegatesToUnderlyingRepository() = runTest {
        val filesDir = temp.newFolder("files")
        val workspaceDir = File(filesDir, WorkspacePaths.DEFAULT_RELATIVE_PATH)
        workspaceDir.mkdirs()
        File(workspaceDir, "Inbox").mkdirs()
        File(workspaceDir, "Inbox/hello.md").writeText("# Hello\n\nBody.")

        val repository = CoalescingNoteRegistryRepository(FileNoteRegistryRepository())
        val result = repository.refresh(config, filesDir)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().inboxSummaries.size)
    }

    @Test
    fun refresh_earlyConcurrentJoinCoalescesToSingleScan() = runTest {
        val filesDir = temp.newFolder("files")
        val delegate = FakeNoteRegistryRepository(refreshDelayMs = 100)
        val repository = CoalescingNoteRegistryRepository(delegate)

        coroutineScope {
            val first = async { repository.refresh(config, filesDir) }
            val second = async { repository.refresh(config, filesDir) }
            first.await()
            second.await()
        }

        assertEquals(1, delegate.refreshCount)
    }

    @Test
    fun refresh_lateJoinAfterInFlightStartRunsSecondScan() = runBlocking {
        val filesDir = temp.newFolder("files")
        val delegate = FakeNoteRegistryRepository(refreshDelayMs = 100)
        val repository = CoalescingNoteRegistryRepository(delegate)

        val first = async { repository.refresh(config, filesDir) }
        delay(60)
        val second = async { repository.refresh(config, filesDir) }
        first.await()
        second.await()

        assertEquals(2, delegate.refreshCount)
    }

    @Test
    fun refresh_waiterGetsResultWhenOwnerCancelled() = runTest {
        val filesDir = temp.newFolder("files")
        val delegate = FakeNoteRegistryRepository(refreshDelayMs = 150)
        val repository = CoalescingNoteRegistryRepository(delegate)

        val waiter = async {
            delay(30)
            repository.refresh(config, filesDir)
        }
        val owner = launch {
            try {
                repository.refresh(config, filesDir)
            } catch (_: CancellationException) {
            }
        }
        delay(80)
        owner.cancel()
        assertTrue(waiter.await().isSuccess)
        assertEquals(1, delegate.refreshCount)
    }
}
