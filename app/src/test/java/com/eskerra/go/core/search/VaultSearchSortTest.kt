package com.eskerra.go.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchSortTest {

    @Test
    fun bestFieldRank_titleBeforePathBeforeBody() {
        assertTrue(
            vaultSearchBestFieldRank(VaultSearchBestField.TITLE) <
                vaultSearchBestFieldRank(VaultSearchBestField.PATH)
        )
        assertTrue(
            vaultSearchBestFieldRank(VaultSearchBestField.PATH) <
                vaultSearchBestFieldRank(VaultSearchBestField.BODY)
        )
    }

    @Test
    fun compare_sortsByScoreDescending() {
        val high =
            VaultSearchNoteResult(
                "b.md",
                "b.md",
                "B",
                VaultSearchBestField.BODY,
                1,
                10f,
                emptyList()
            )
        val low =
            VaultSearchNoteResult(
                "a.md",
                "a.md",
                "A",
                VaultSearchBestField.BODY,
                1,
                1f,
                emptyList()
            )
        assertEquals(
            listOf("b.md", "a.md"),
            listOf(low, high).sortedWith(::compareVaultSearchNotes).map {
                it.uri
            }
        )
    }

    @Test
    fun compare_titleBeatsBodyAtSameScore() {
        val body =
            VaultSearchNoteResult(
                "b.md",
                "b.md",
                "B",
                VaultSearchBestField.BODY,
                1,
                5f,
                emptyList()
            )
        val title =
            VaultSearchNoteResult(
                "a.md",
                "a.md",
                "A",
                VaultSearchBestField.TITLE,
                1,
                5f,
                emptyList()
            )
        assertEquals(
            listOf("a.md", "b.md"),
            listOf(body, title).sortedWith(::compareVaultSearchNotes).map {
                it.uri
            }
        )
    }
}
