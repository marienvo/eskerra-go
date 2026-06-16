package com.eskerra.go.data.search

import com.eskerra.go.core.search.VaultSearchError
import com.eskerra.go.core.search.VaultSearchException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchSqliteErrorMapperTest {

    @Test
    fun map_fts5Unsupported() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException("no such module: fts5")
        )
        assertTrue(mapped.error is VaultSearchError.Fts5Unsupported)
    }

    @Test
    fun map_indexCorrupt() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException("database disk image is malformed")
        )
        assertTrue(mapped.error is VaultSearchError.IndexCorrupt)
        assertTrue(mapped.error.canRetry())
    }

    @Test
    fun map_preservesVaultSearchException() {
        val original = VaultSearchException(VaultSearchError.QueryFailed)
        val mapped = VaultSearchSqliteErrorMapper.map(original)
        assertEquals(original, mapped)
    }
}
