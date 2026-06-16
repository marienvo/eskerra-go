package com.eskerra.go.data.search

import com.eskerra.go.core.search.VaultSearchError
import com.eskerra.go.core.search.VaultSearchException
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteVaultSearchRepositoryMaintainErrorTest {

    @Test
    fun maintainRecoveryPath_mapsRepairFailureToVaultSearchException() {
        val result = simulateMaintainResult(
            initialError = RuntimeException("database disk image is malformed"),
            repairError = RuntimeException("unable to open database file")
        )

        val error = result.exceptionOrNull()
        assertTrue(error is VaultSearchException)
        assertTrue((error as VaultSearchException).error is VaultSearchError.IndexOpenFailed)
        assertTrue(error.error.canRetry())
    }

    @Test
    fun maintainRecoveryPath_preservesNonRetryableMappedError() {
        val mapped = VaultSearchException(VaultSearchError.WorkspaceUnavailable)
        val result = simulateMaintainResult(
            initialError = RuntimeException("database disk image is malformed"),
            repairError = mapped
        )

        val error = result.exceptionOrNull()
        assertTrue(error is VaultSearchException)
        assertTrue((error as VaultSearchException).error is VaultSearchError.WorkspaceUnavailable)
    }

    private fun simulateMaintainResult(
        initialError: Throwable,
        repairError: Throwable
    ): Result<Unit> = runCatching<Unit> {
        throw initialError
    }.recoverCatching { error ->
        val mapped = VaultSearchSqliteErrorMapper.map(error)
        if (mapped.error.canRetry()) {
            throw repairError
        } else {
            throw mapped
        }
    }.mapFailure()

    private fun <T> Result<T>.mapFailure(): Result<T> = recoverCatching { error ->
        throw VaultSearchSqliteErrorMapper.map(error)
    }
}
