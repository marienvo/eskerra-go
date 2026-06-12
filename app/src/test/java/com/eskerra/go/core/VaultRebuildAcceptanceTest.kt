package com.eskerra.go.core

import com.eskerra.go.core.search.SearchRanker
import com.eskerra.go.core.search.VaultSearchBestField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated coverage for spec §15 scenarios that map to pure core behavior.
 * UI/render/image paths remain manual or covered in feature-specific tests.
 */
class VaultRebuildAcceptanceTest {

    @Test
    fun search_titleMatchRanksAboveBodyOnly() {
        val titleHit = SearchRanker.rank(
            SearchRankerTestSupport.candidate(title = "Alpha Note"),
            "alpha",
            listOf("alpha")
        )
        val bodyOnly = SearchRanker.rank(
            SearchRankerTestSupport.candidate(body = "alpha appears here"),
            "alpha",
            listOf("alpha")
        )
        assertTrue(titleHit.score > bodyOnly.score)
        assertEquals(VaultSearchBestField.TITLE, titleHit.bestField)
    }

    @Test
    fun search_fuzzyPrefixOnFilename() {
        val ranked = SearchRanker.rank(
            SearchRankerTestSupport.candidate(filename = "projectplan.md", title = "Plan"),
            "proj",
            listOf("proj")
        )
        assertTrue(ranked.score >= 25_000f)
    }
}

/** Shared fixtures for acceptance tests without duplicating SearchRankerTest helpers. */
internal object SearchRankerTestSupport {
    fun candidate(
        uri: String = "u.md",
        relPath: String = "Inbox/u.md",
        title: String = "Title",
        filename: String = "u.md",
        body: String = "",
        bm25: Float = 0f
    ) = com.eskerra.go.core.search.SearchCandidate(uri, relPath, title, filename, body, bm25)
}
