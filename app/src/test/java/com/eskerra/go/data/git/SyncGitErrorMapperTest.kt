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
}
