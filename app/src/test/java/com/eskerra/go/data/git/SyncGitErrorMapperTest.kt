package com.eskerra.go.data.git

import com.eskerra.go.core.model.SyncError
import com.eskerra.go.core.model.SyncException
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncGitErrorMapperTest {

    @Test
    fun mapFailure_localBranchNotFound_returnsTypedError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            IllegalStateException("local branch not found: main"),
            branch = "main"
        )

        val error = (mapped as SyncException).error
        assertTrue(error is SyncError.LocalBranchNotFound)
        assertTrue(error.message().contains("main"))
    }

    @Test
    fun mapFailure_remoteBranchNotFound_returnsTypedError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            IllegalStateException("remote branch not found: main"),
            branch = "main"
        )

        val error = (mapped as SyncException).error
        assertTrue(error is SyncError.RemoteBranchNotFound)
    }

    @Test
    fun mapFailure_authenticationFailed_returnsSafeError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            RuntimeException("authentication failed for user"),
            branch = "main"
        )

        assertTrue((mapped as SyncException).error is SyncError.AuthenticationFailed)
    }

    @Test
    fun mapFailure_networkUnavailable_returnsSafeError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            RuntimeException("Connection refused"),
            branch = "main"
        )

        assertTrue((mapped as SyncException).error is SyncError.RemoteUnavailable)
    }

    @Test
    fun mapFailure_pushRejected_returnsSafeError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            RuntimeException("push rejected for refs/heads/main"),
            branch = "main"
        )

        assertTrue((mapped as SyncException).error is SyncError.PushRejected)
    }

    @Test
    fun mapFailure_diverged_returnsSafeError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            RuntimeException("not fast-forwardable"),
            branch = "main"
        )

        assertTrue((mapped as SyncException).error is SyncError.Diverged)
    }

    @Test
    fun mapFailure_manualIntervention_returnsSafeError() {
        val mapped = SyncGitErrorMapper.mapFailure(
            RuntimeException("MERGING unmerged paths"),
            branch = "main"
        )

        assertTrue((mapped as SyncException).error is SyncError.ManualInterventionRequired)
    }
}
