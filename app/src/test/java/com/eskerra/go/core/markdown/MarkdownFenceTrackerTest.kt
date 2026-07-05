package com.eskerra.go.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownFenceTrackerTest {

    @Test
    fun parseOpening_acceptsTripleBacktickFence() {
        val open = MarkdownFenceTracker.parseOpening("```kotlin")
        assertNotNull(open)
        assertEquals('`', open!!.char)
        assertEquals(3, open.length)
    }

    @Test
    fun parseOpening_acceptsTripleTildeFence() {
        val open = MarkdownFenceTracker.parseOpening("~~~")
        assertNotNull(open)
        assertEquals('~', open!!.char)
        assertEquals(3, open.length)
    }

    @Test
    fun parseOpening_acceptsLongerBacktickFence() {
        val open = MarkdownFenceTracker.parseOpening("````")
        assertNotNull(open)
        assertEquals(4, open!!.length)
    }

    @Test
    fun parseOpening_rejectsInlineBackticksAfterOpeningRun() {
        assertNull(MarkdownFenceTracker.parseOpening("```not`allowed"))
    }

    @Test
    fun parseOpening_allowsIndentedFence() {
        val open = MarkdownFenceTracker.parseOpening("   ```")
        assertNotNull(open)
        assertEquals(3, open!!.length)
    }

    @Test
    fun isClosing_requiresMatchingMarkerType() {
        val backtickOpen = MarkdownFenceTracker.OpenFence('`', 3)
        assertFalse(MarkdownFenceTracker.isClosing("~~~", backtickOpen))
    }

    @Test
    fun isClosing_requiresAtLeastOpeningLength() {
        val open = MarkdownFenceTracker.OpenFence('`', 4)
        assertFalse(MarkdownFenceTracker.isClosing("```", open))
        assertTrue(MarkdownFenceTracker.isClosing("````", open))
    }

    @Test
    fun isClosing_allowsLongerClosingRunForTildes() {
        val open = MarkdownFenceTracker.OpenFence('~', 3)
        assertTrue(MarkdownFenceTracker.isClosing("~~~~", open))
    }

    @Test
    fun isClosing_allowsTrailingWhitespaceOnly() {
        val open = MarkdownFenceTracker.OpenFence('`', 3)
        assertTrue(MarkdownFenceTracker.isClosing("```  \t", open))
        assertFalse(MarkdownFenceTracker.isClosing("``` tail", open))
    }
}
