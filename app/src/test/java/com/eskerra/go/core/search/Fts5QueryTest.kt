package com.eskerra.go.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Fts5QueryTest {

    @Test
    fun buildSafeMatch_joinsQuotedTokensWithImplicitAnd() {
        assertEquals(
            "\"foo\" \"bar\"",
            Fts5Query.buildSafeMatch(Fts5Query.tokenizeQuery("foo bar"))
        )
    }

    @Test
    fun buildSafeMatch_preservesColonsInPhrase() {
        assertEquals(
            "\"todo: fix\"",
            Fts5Query.buildSafeMatch(listOf("TODO: fix"))
        )
    }

    @Test
    fun buildSafeMatch_stripsLeadingMinus() {
        assertEquals("\"foo\"", Fts5Query.buildSafeMatch(Fts5Query.tokenizeQuery("-foo")))
    }

    @Test
    fun buildSafeMatch_dropsOperatorOnlyTokens() {
        assertNull(Fts5Query.buildSafeMatch(listOf("AND")))
    }

    @Test
    fun tokenizeQuery_splitsOnWhitespace() {
        assertEquals(listOf("a", "b"), Fts5Query.tokenizeQuery("  a \t b  "))
    }

    @Test
    fun buildSafeMatch_stripsParentheses() {
        assertEquals("\"foo\"", Fts5Query.buildSafeMatch(listOf("(foo)")))
    }

    @Test
    fun buildSafeMatch_supportsUnicodeTokens() {
        assertEquals(
            "\"café\" \"🎉\"",
            Fts5Query.buildSafeMatch(Fts5Query.tokenizeQuery("café 🎉"))
        )
        assertEquals("\"你好\"", Fts5Query.buildSafeMatch(listOf("你好")))
    }

    @Test
    fun buildSafeMatch_returnsNullForBlankQuery() {
        assertNull(Fts5Query.buildSafeMatch(Fts5Query.tokenizeQuery("   ")))
        assertNull(Fts5Query.buildSafeMatch(Fts5Query.tokenizeQuery("OR AND")))
    }
}
