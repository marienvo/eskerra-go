package com.eskerra.go.feature.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MIN_MS = 450L

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSpinnerVisibilityTest {

    @Test
    fun instantSyncStillHoldsTrueForAtLeastMinMs() = runTest {
        val source = MutableStateFlow(true)
        val emissions = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            source.holdTrueAtLeast(MIN_MS).collect { emissions += testScheduler.currentTime to it }
        }
        testScheduler.runCurrent()

        // Sync finishes almost instantly.
        source.value = false
        testScheduler.advanceTimeBy(MIN_MS - 1)
        testScheduler.runCurrent()
        assertEquals(listOf(0L to true), emissions)

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        assertEquals(listOf(0L to true, MIN_MS to false), emissions)

        job.cancel()
    }

    @Test
    fun longSyncIsNotTruncated() = runTest {
        val source = MutableStateFlow(true)
        val emissions = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            source.holdTrueAtLeast(MIN_MS).collect { emissions += testScheduler.currentTime to it }
        }
        testScheduler.runCurrent()

        // Sync stays visibly running well past the hold floor.
        testScheduler.advanceTimeBy(2_000L)
        testScheduler.runCurrent()
        assertEquals(listOf(0L to true), emissions)

        source.value = false
        testScheduler.advanceTimeBy(MIN_MS + 1)
        testScheduler.runCurrent()
        assertEquals(listOf(0L to true, (2_000L + MIN_MS) to false), emissions)

        job.cancel()
    }

    @Test
    fun reSyncDuringHoldWindowCancelsThePendingFalse() = runTest {
        val source = MutableStateFlow(true)
        val emissions = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            source.holdTrueAtLeast(MIN_MS).collect { emissions += testScheduler.currentTime to it }
        }
        testScheduler.runCurrent()

        source.value = false
        testScheduler.advanceTimeBy(MIN_MS / 2)
        testScheduler.runCurrent()

        // A new sync starts before the hold window elapses: the pending false must never surface.
        source.value = true
        testScheduler.advanceTimeBy(MIN_MS)
        testScheduler.runCurrent()

        assertEquals(listOf(0L to true), emissions)

        job.cancel()
    }
}
