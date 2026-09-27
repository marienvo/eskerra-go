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
            markdownLength = 1000,
            visibleStartFraction = 0.45f,
            visibleEndFraction = 0.55f
        )

        assertEquals(listOf(NoteId("Near.md"), NoteId("Far.md")), result)
    }

    @Test
    fun orderByViewport_ordersByDistanceOutsideWindow() {
        val closest = PrefetchLinkTargets.Target(NoteId("Closest.md"), sourceOffset = 600)
        val furthest = PrefetchLinkTargets.Target(NoteId("Furthest.md"), sourceOffset = 900)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(furthest, closest),
            markdownLength = 1000,
            visibleStartFraction = 0f,
            visibleEndFraction = 0.1f
        )

        assertEquals(listOf(NoteId("Closest.md"), NoteId("Furthest.md")), result)
    }

    @Test
    fun orderByViewport_fallsBackToIncomingOrder_whenLengthNotPositive() {
        val a = PrefetchLinkTargets.Target(NoteId("A.md"), sourceOffset = 5)
        val b = PrefetchLinkTargets.Target(NoteId("B.md"), sourceOffset = 1)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(a, b),
            markdownLength = 0,
            visibleStartFraction = 0f,
            visibleEndFraction = 1f
        )

        assertEquals(listOf(NoteId("A.md"), NoteId("B.md")), result)
    }
}
