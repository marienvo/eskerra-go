package com.eskerra.go.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {

    private fun candidate(
        uri: String = "u.md",
        relPath: String = "Inbox/u.md",
        title: String = "Title",
        filename: String = "u.md",
        body: String = "",
        bm25: Float = 0f
    ) = SearchCandidate(uri, relPath, title, filename, body, bm25)

    @Test
    fun rank_exactTitleTierBeatsBodyOnly() {
        val titleHit = SearchRanker.rank(
            candidate(title = "Lisanne", relPath = "people/Lisanne.md"),
            "lisanne",
            listOf("lisanne")
        )
        val bodyOnly = SearchRanker.rank(
            candidate(body = "lisanne appears in the body"),
            "lisanne",
            listOf("lisanne")
        )
        assertTrue(titleHit.score > bodyOnly.score)
        assertEquals(VaultSearchBestField.TITLE, titleHit.bestField)
    }

    @Test
    fun rank_prefixTierBeatsFuzzyOnly() {
        val prefix = SearchRanker.rank(
            candidate(filename = "projectplan.md", title = "Plan"),
            "proj",
            listOf("proj")
        )
        val fuzzyOnly = SearchRanker.rank(
            candidate(filename = "alpha.md", title = "alpha"),
            "proj",
            listOf("proj")
        )
        assertTrue(prefix.score > fuzzyOnly.score)
    }

    @Test
    fun rank_bm25TieBreakWithinSameTier() {
        val betterBm25 = SearchRanker.rank(
            candidate(title = "alpha note", bm25 = -5f),
            "alpha",
            listOf("alpha")
        )
        val worseBm25 = SearchRanker.rank(
            candidate(title = "alpha note", bm25 = -10f),
            "alpha",
            listOf("alpha")
        )
        assertTrue(betterBm25.score > worseBm25.score)
    }

    @Test
    fun rank_matchCountCountsMultipleTokens() {
        val ranked = SearchRanker.rank(
            candidate(title = "foo bar note", body = "foo and bar"),
            "foo bar",
            listOf("foo", "bar")
        )
        assertTrue(ranked.matchCount >= 2)
    }

    @Test
    fun rank_snippetPrefersFullQueryLine() {
        val ranked = SearchRanker.rank(
            candidate(body = "intro\nalpha beta line\noutro"),
            "alpha beta",
            listOf("alpha", "beta")
        )
        assertEquals(2, ranked.snippetLine)
        assertEquals("alpha beta line", ranked.snippetText)
    }
}
