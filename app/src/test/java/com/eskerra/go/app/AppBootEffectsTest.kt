package com.eskerra.go.app

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBootEffectsTest {

    @Test
    fun firstOnStart_doesNotAutoSync() {
        assertFalse(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_START,
                isFirstStart = true
            )
        )
    }

    @Test
    fun laterOnStart_autoSyncs() {
        assertTrue(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_START,
                isFirstStart = false
            )
        )
    }

    @Test
    fun nonStartEvent_doesNotAutoSync() {
        assertFalse(
            shouldAutoSyncOnLifecycleEvent(
                event = Lifecycle.Event.ON_RESUME,
                isFirstStart = false
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
}
