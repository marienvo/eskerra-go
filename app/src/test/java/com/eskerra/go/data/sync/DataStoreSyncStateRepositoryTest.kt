package com.eskerra.go.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.eskerra.go.core.model.DurableSyncStatus
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSyncStateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createRepository(): DataStoreSyncStateRepository {
        val testFile = File(tempFolder.newFolder(), "test_sync_state.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        return DataStoreSyncStateRepository(dataStore)
    }

    @Test
    fun initialRecord_isSyncedAtZero() = runTest {
        val repository = createRepository()
        val record = repository.getRecord()

        assertEquals(0L, record.requestedGeneration)
        assertEquals(0L, record.completedGeneration)
        assertFalse(record.isPending)
        assertTrue(record.status is DurableSyncStatus.Synced)
    }

    @Test
    fun markGenerationRequested_incrementsMonotonicallyAndTransitionsToPending() = runTest {
        val repository = createRepository()

        val gen1 = repository.markGenerationRequested()
        assertEquals(1L, gen1)
        val record1 = repository.getRecord()
        assertEquals(1L, record1.requestedGeneration)
        assertEquals(0L, record1.completedGeneration)
        assertTrue(record1.isPending)
        assertEquals(DurableSyncStatus.Pending, record1.status)

        val gen2 = repository.markGenerationRequested()
        assertEquals(2L, gen2)
        val record2 = repository.getRecord()
        assertEquals(2L, record2.requestedGeneration)
        assertEquals(0L, record2.completedGeneration)
        assertTrue(record2.isPending)
        assertEquals(DurableSyncStatus.Pending, record2.status)
    }

    @Test
    fun markGenerationCompleted_advancesCompletedAndSetsSyncedWhenUpToDate() = runTest {
        val repository = createRepository()
        repository.markGenerationRequested() // requested = 1
        repository.updateStatus(
            DurableSyncStatus.Running(
                step = "Pushing",
                startedAtEpochMs = 1000L
            )
        )

        repository.markGenerationCompleted(snapshot = 1L, completedAtEpochMs = 2000L)

        val record = repository.getRecord()
        assertEquals(1L, record.requestedGeneration)
        assertEquals(1L, record.completedGeneration)
        assertFalse(record.isPending)
        assertEquals(DurableSyncStatus.Synced(2000L), record.status)
    }

    @Test
    fun markGenerationCompleted_leavesPendingWhenNewerGenerationsWereRequested() = runTest {
        val repository = createRepository()
        val snapshot = repository.markGenerationRequested() // requested = 1
        repository.updateStatus(
            DurableSyncStatus.Running(
                step = "Pushing",
                startedAtEpochMs = 1000L
            )
        )

        // Mutation happens during sync!
        repository.markGenerationRequested() // requested = 2

        repository.markGenerationCompleted(snapshot = snapshot, completedAtEpochMs = 2000L)

        val record = repository.getRecord()
        assertEquals(2L, record.requestedGeneration)
        assertEquals(1L, record.completedGeneration)
        assertTrue(record.isPending)
        assertEquals(DurableSyncStatus.Pending, record.status)
    }

    @Test
    fun reconcileOrphanedRunning_transitionsPendingOnlyWhenNoActiveWorker() = runTest {
        val repository = createRepository()
        repository.updateStatus(DurableSyncStatus.Running("Fetching", 100L))

        repository.reconcileOrphanedRunning(hasActiveWorker = true)
        assertTrue(repository.getRecord().status is DurableSyncStatus.Running)

        repository.reconcileOrphanedRunning(hasActiveWorker = false)
        assertEquals(DurableSyncStatus.Pending, repository.getRecord().status)
    }

    @Test
    fun updateStatus_persistsRetryingAndBlocked() = runTest {
        val repository = createRepository()

        repository.updateStatus(
            DurableSyncStatus.Retrying(
                reason = "Timeout",
                nextAttemptAtEpochMs = 5000L
            )
        )
        val retrying = repository.getRecord().status
        assertTrue(retrying is DurableSyncStatus.Retrying)
        assertEquals("Timeout", (retrying as DurableSyncStatus.Retrying).reason)
        assertEquals(5000L, retrying.nextAttemptAtEpochMs)

        repository.updateStatus(DurableSyncStatus.Blocked(reason = "Auth failed"))
        val blocked = repository.getRecord().status
        assertTrue(blocked is DurableSyncStatus.Blocked)
        assertEquals("Auth failed", (blocked as DurableSyncStatus.Blocked).reason)
    }
}
