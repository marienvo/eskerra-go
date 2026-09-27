package com.eskerra.go.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteOpenGateTest {

    @Test
    fun open_newerTapCancelsOlderWarmBeforeItNavigates() = runTest {
        val gate = NoteOpenGate()
        val firstStarted = CompletableDeferred<Unit>()
        val opened = mutableListOf<String>()

        gate.open(
            scope = this,
            warm = {
                firstStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
            onReadyToNavigate = { opened += "first" }
        )
        firstStarted.await()
        gate.open(scope = this, warm = {}, onReadyToNavigate = { opened += "second" })

        runCurrent()

        assertEquals(listOf("second"), opened)
    }

    @Test
    fun open_sourceScopeCancellationPreventsDelayedNavigation() = runTest {
        val gate = NoteOpenGate()
        val source = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val opened = mutableListOf<String>()
        gate.open(
            scope = source,
            warm = { CompletableDeferred<Unit>().await() },
            onReadyToNavigate = { opened += "note" }
        )
        runCurrent()

        source.cancel()
        runCurrent()

        assertEquals(emptyList<String>(), opened)
    }
}
