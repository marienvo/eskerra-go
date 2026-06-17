package com.eskerra.go.data.search

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchTokenizerTest {

    @Test
    fun clauseForSdk_api30Plus_usesRemoveDiacritics2() {
        assertEquals(
            VaultSearchTokenizer.FTS_TOKENIZE_API_30_PLUS,
            VaultSearchTokenizer.clauseForSdk(Build.VERSION_CODES.R)
        )
    }

    @Test
    fun clauseForSdk_belowApi30_prefersRemoveDiacritics2() {
        assertEquals(
            VaultSearchTokenizer.FTS_TOKENIZE_API_30_PLUS,
            VaultSearchTokenizer.clauseForSdk(Build.VERSION_CODES.Q)
        )
    }

    @Test
    fun clauseCandidates_belowApi30_tryMode2BeforeLegacy() {
        val candidates = VaultSearchTokenizer.clauseCandidatesForSdk(Build.VERSION_CODES.Q)
        assertEquals(VaultSearchTokenizer.FTS_TOKENIZE_API_30_PLUS, candidates.first())
        assertTrue(VaultSearchTokenizer.FTS_TOKENIZE_LEGACY in candidates)
    }

    @Test
    fun clauseCandidates_includeUnicode61Fallback() {
        val candidates = VaultSearchTokenizer.clauseCandidatesForSdk(Build.VERSION_CODES.R)
        assertEquals(VaultSearchTokenizer.FTS_TOKENIZE_API_30_PLUS, candidates.first())
        assertTrue(VaultSearchTokenizer.FTS_TOKENIZE_UNICODE61 in candidates)
        assertTrue(VaultSearchTokenizer.FTS_TOKENIZE_PORTER in candidates)
    }
}
