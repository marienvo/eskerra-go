package com.eskerra.go.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileDifferTest {

    @Test
    fun diff_detectsAddedUpdatedRemoved() {
        val diff = ReconcileDiffer.diff(
            inDb = mapOf(
                "u1" to FileSnapshot(10, 100),
                "u2" to FileSnapshot(20, 200),
                "gone" to FileSnapshot(5, 50)
            ),
            onDisk = mapOf(
                "u1" to FileSnapshot(10, 100),
                "u2" to FileSnapshot(99, 200),
                "u3" to FileSnapshot(7, 70)
            )
        )
        assertEquals(listOf("gone"), diff.removed.sorted())
        assertEquals(listOf("u3"), diff.added.sorted())
        assertEquals(listOf("u2"), diff.updated.sorted())
    }
}
