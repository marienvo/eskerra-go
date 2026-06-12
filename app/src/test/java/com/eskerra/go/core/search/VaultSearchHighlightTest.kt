package com.eskerra.go.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchHighlightTest {

    @Test
    fun needles_includeFullQueryAndLongTokens() {
        assertEquals(listOf("foo bar", "foo", "bar"), vaultSearchHighlightNeedles("Foo BAR"))
    }

    @Test
    fun needles_dedupesWhenFullQueryEqualsToken() {
        assertEquals(listOf("hello"), vaultSearchHighlightNeedles("hello"))
    }

    @Test
    fun needles_omitShortTokens() {
        assertEquals(listOf("ab cde", "cde"), vaultSearchHighlightNeedles("ab cde"))
        assertTrue("ab".length < VAULT_SEARCH_HIGHLIGHT_MIN_TOKEN_CHARS)
    }

    @Test
    fun needles_emptyForBlankQuery() {
        assertEquals(emptyList<String>(), vaultSearchHighlightNeedles(""))
    }

    @Test
    fun segments_emptyForEmptyText() {
        assertEquals(
            emptyList<VaultSearchHighlightSegment>(),
            vaultSearchHighlightSegments("", "foo")
        )
    }

    @Test
    fun segments_singleNonHighlightWhenQueryEmpty() {
        assertEquals(
            listOf(VaultSearchHighlightSegment("Hello", false)),
            vaultSearchHighlightSegments("Hello", "")
        )
    }

    @Test
    fun segments_highlightCaseInsensitivelyPreserveCasing() {
        assertEquals(
            listOf(
                VaultSearchHighlightSegment("Hello ", false),
                VaultSearchHighlightSegment("FOo", true)
            ),
            vaultSearchHighlightSegments("Hello FOo", "foo")
        )
    }

    @Test
    fun segments_mergePhraseAndTokenRanges() {
        assertEquals(
            listOf(
                VaultSearchHighlightSegment("X ", false),
                VaultSearchHighlightSegment("foo bar", true),
                VaultSearchHighlightSegment(" Y", false)
            ),
            vaultSearchHighlightSegments("X foo bar Y", "foo bar")
        )
    }

    @Test
    fun segments_mergeAdjacentHighlights() {
        assertEquals(
            listOf(VaultSearchHighlightSegment("foofoo", true)),
            vaultSearchHighlightSegments("foofoo", "foo")
        )
    }

    @Test
    fun segments_handleMultipleSeparateMatches() {
        assertEquals(
            listOf(
                VaultSearchHighlightSegment("a ", false),
                VaultSearchHighlightSegment("foo", true),
                VaultSearchHighlightSegment(" b ", false),
                VaultSearchHighlightSegment("foo", true)
            ),
            vaultSearchHighlightSegments("a foo b foo", "foo")
        )
    }
}
