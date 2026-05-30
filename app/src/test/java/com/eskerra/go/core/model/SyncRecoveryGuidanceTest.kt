package com.eskerra.go.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRecoveryGuidanceTest {

    @Test
    fun authFailure_hintDoesNotContainToken() {
        val token = "super-secret-token-value"
        val hint = SyncRecoveryGuidance.forError(SyncError.AuthenticationFailed).hint

        assertFalse(hint.contains(token))
    }

    @Test
    fun authFailure_suggestsOpenSettings() {
        val action = SyncRecoveryGuidance.forError(SyncError.AuthenticationFailed)

        assertTrue(action.suggestOpenSettings)
    }

    @Test
    fun diverged_hintIsSafeAndNonDestructive() {
        val hint = SyncRecoveryGuidance.forError(SyncError.Diverged).hint

        assertFalse(hint.contains("reset --hard", ignoreCase = true))
        assertFalse(hint.contains("git reset", ignoreCase = true))
        assertTrue(hint.contains("Git"))
    }

    @Test
    fun manualIntervention_hintDoesNotContainRawException() {
        val hint = SyncRecoveryGuidance.forError(SyncError.ManualInterventionRequired).hint

        assertFalse(hint.contains("Exception"))
        assertFalse(hint.contains("stack", ignoreCase = true))
    }

    @Test
    fun remoteUnavailable_localNotesRemainAvailable() {
        val action = SyncRecoveryGuidance.forError(SyncError.RemoteUnavailable)

        assertTrue(action.localNotesAvailable)
    }
}
