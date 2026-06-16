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
    fun map_unknownTokenizer_mapsToIndexCorrupt() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException("unknown tokenizer")
        )
        assertTrue(mapped.error is VaultSearchError.IndexCorrupt)
        assertTrue(mapped.error.canRetry())
    }

    @Test
    fun map_tokenizerNotSupported_mapsToIndexCorrupt_notFts5Unsupported() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException(
                "fts5: tokenizer option remove_diacritics 2 is not supported"
            )
        )
        assertTrue(mapped.error is VaultSearchError.IndexCorrupt)
        assertTrue(mapped.error.canRetry())
    }

    @Test
    fun map_readsCauseChain() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException(
                "wrapper",
                RuntimeException("database disk image is malformed")
            )
        )
        assertTrue(mapped.error is VaultSearchError.IndexCorrupt)
    }

    @Test
    fun map_missingIndexMetaTable_mapsToIndexCorrupt() {
        val mapped = VaultSearchSqliteErrorMapper.map(
            RuntimeException("no such table: index_meta")
        )
        assertTrue(mapped.error is VaultSearchError.IndexCorrupt)
        assertTrue(mapped.error.canRetry())
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

    @Test
    fun diagnosticDetail_prefersRootMessage() {
        val detail = VaultSearchSqliteErrorMapper.diagnosticDetail(
            RuntimeException("unknown tokenizer option remove_diacritics 2")
        )
        assertEquals("unknown tokenizer option remove_diacritics 2", detail)
    }
}
