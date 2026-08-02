package com.eskerra.go.app

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBootEffectsTest {

    @Test
    fun onStart_beforeAccepting_doesNotAutoSync() {
        assertFalse(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_START,
                accepting = false
            )
        )
    }

    @Test
    fun onStart_afterAccepting_autoSyncs() {
        assertTrue(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_START,
                accepting = true
            )
        )
    }

    @Test
    fun nonStartEvent_doesNotAutoSync() {
        assertFalse(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_RESUME,
                accepting = true
            )
        )
    }

    @Test
    fun bootSync_doesNotFireBeforeLaunchSettles() {
        assertFalse(shouldTriggerBootSync(launchSettled = false, alreadyRequested = false))
    }

    @Test
    fun bootSync_firesOnceLaunchSettles() {
        assertTrue(shouldTriggerBootSync(launchSettled = true, alreadyRequested = false))
    }

    @Test
    fun bootSync_doesNotFireAgainOnceAlreadyRequested() {
        assertFalse(shouldTriggerBootSync(launchSettled = true, alreadyRequested = true))
    }

    @Test
    fun bootSync_mayFireAgainForNewViewModelInstanceAfterSettle() {
        // A recreated AppSyncViewModel starts with alreadyRequested = false even when
        // launchSettled is already true; the composition-lifetime flag must not block it.
        assertTrue(shouldTriggerBootSync(launchSettled = true, alreadyRequested = false))
    }
}
