package com.eskerra.go.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.eskerra.go.core.model.DurableSyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkManagerVaultSyncSchedulerTest {

    @Test
    fun scheduleSync_usesAppendOrReplacePolicy() {
        var capturedPolicy: ExistingWorkPolicy? = null
        var capturedWorkName: String? = null
        var capturedRequest: OneTimeWorkRequest? = null

        val repo = FakeSyncStateRepository()
        val scheduler = WorkManagerVaultSyncScheduler(
            context = object : android.content.ContextWrapper(null) {},
            syncStateRepository = repo,
            enqueueWork = { name, policy, request ->
                capturedWorkName = name
                capturedPolicy = policy
                capturedRequest = request
            }
        )

        scheduler.scheduleSync()

        assertEquals(WorkManagerVaultSyncScheduler.WORK_NAME_VAULT_SYNC, capturedWorkName)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, capturedPolicy)
        val tags = capturedRequest?.tags.orEmpty()
        assertTrue(tags.contains(WorkManagerVaultSyncScheduler.TAG_VAULT_SYNC))
    }

    @Test
    fun reconcile_whenNoActiveWorkerAndRunning_transitionsToPendingAndSchedules() = runTest {
        var enqueued = false
        val repo = FakeSyncStateRepository()
        repo.updateStatus(DurableSyncStatus.Running("Fetching", 1000L))

        val scheduler = WorkManagerVaultSyncScheduler(
            context = object : android.content.ContextWrapper(null) {},
            syncStateRepository = repo,
            enqueueWork = { _, _, _ -> enqueued = true },
            getActiveWorkInfos = { emptyList() }
        )

        scheduler.reconcile()

        assertTrue(enqueued)
        assertEquals(DurableSyncStatus.Pending, repo.getRecord().status)
    }
}
