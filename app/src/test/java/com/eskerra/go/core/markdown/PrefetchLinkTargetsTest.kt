package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefetchLinkTargetsTest {

    @Test
    fun orderByViewport_prioritizesTargetInsideVisibleWindow() {
        val near = PrefetchLinkTargets.Target(NoteId("Near.md"), sourceOffset = 500)
        val far = PrefetchLinkTargets.Target(NoteId("Far.md"), sourceOffset = 10)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(far, near),
            visibleStartOffset = 450,
            visibleEndOffset = 550
        )

        assertEquals(listOf(NoteId("Near.md"), NoteId("Far.md")), result)
    }

    @Test
    fun orderByViewport_ordersByDistanceOutsideWindow() {
        val closest = PrefetchLinkTargets.Target(NoteId("Closest.md"), sourceOffset = 600)
        val furthest = PrefetchLinkTargets.Target(NoteId("Furthest.md"), sourceOffset = 900)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(furthest, closest),
            visibleStartOffset = 0,
            visibleEndOffset = 100
        )

        assertEquals(listOf(NoteId("Closest.md"), NoteId("Furthest.md")), result)
    }

    @Test
    fun orderByViewport_ordersUsingOffsetsWithoutDocumentLength() {
        val a = PrefetchLinkTargets.Target(NoteId("A.md"), sourceOffset = 5)
        val b = PrefetchLinkTargets.Target(NoteId("B.md"), sourceOffset = 1)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(a, b),
            visibleStartOffset = 1,
            visibleEndOffset = 1
        )

        assertEquals(listOf(NoteId("B.md"), NoteId("A.md")), result)
    }
}
