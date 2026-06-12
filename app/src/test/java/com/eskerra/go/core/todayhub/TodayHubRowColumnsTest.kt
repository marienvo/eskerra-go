package com.eskerra.go.core.todayhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seeded from `todayHub.test.ts` (`splitTodayRowIntoColumns` / `mergeTodayRowColumns`). */
class TodayHubRowColumnsTest {

    private fun roundTrip(sections: List<String>, count: Int): String {
        val merged = TodayHubRowColumns.merge(sections)
        return TodayHubRowColumns.merge(TodayHubRowColumns.split(merged, count))
    }

    @Test
    fun singleColumnIsIdentity() {
        val raw = "# Hi\n\nfoo"
        assertEquals(listOf(raw), TodayHubRowColumns.split(raw, 1))
    }

    @Test
    fun splitsOnDelimiterAndMergesBack() {
        val merged = TodayHubRowColumns.merge(
            listOf("# 2026-04-06\n\ndefault col", "actions\n\nmore")
        )
        val parts = TodayHubRowColumns.split(merged, 2)
        assertEquals(2, parts.size)
        assertEquals("# 2026-04-06\n\ndefault col", parts[0])
        assertEquals("actions\n\nmore", parts[1])
        assertEquals(merged, roundTrip(listOf(parts[0], parts[1]), 2))
    }

    @Test
    fun padsWhenMultiColumnButNoDelimiter() {
        assertEquals(listOf("only default", "", ""), TodayHubRowColumns.split("only default", 3))
    }

    @Test
    fun mergesExtraChunksIntoLastColumn() {
        val text = "a\n\n::today-section::\n\nb\n\n::today-section::\n\nc\n\n::today-section::\n\nd"
        val parts = TodayHubRowColumns.split(text, 2)
        assertEquals("a", parts[0])
        assertEquals("b\n\n\nc\n\n\nd", parts[1])
    }

    @Test
    fun stripsSpuriousMarkerOnlyLines() {
        val text = "123\n\n::today-section::\n\n::today-section::\n\nsdf\n\n::today-section::"
        val parts = TodayHubRowColumns.split(text, 2)
        assertEquals("123", parts[0])
        assertEquals("\nsdf\n\n\n", parts[1])
        assertFalse(parts[1].contains("::today-section::"))
    }

    @Test
    fun splitsWhenSectionEndsAtEofAfterMarker() {
        assertEquals(listOf("1", ""), TodayHubRowColumns.split("1\n\n::today-section::", 2))
    }

    @Test
    fun splitsWithSingleNewlineBeforeMarker() {
        assertEquals(listOf("1", ""), TodayHubRowColumns.split("1\n::today-section::\n\n", 2))
    }

    @Test
    fun keepsEmptyMiddleColumnSlots() {
        val sections = listOf("left", "", "right")
        val merged = TodayHubRowColumns.merge(sections)
        assertEquals(sections, TodayHubRowColumns.split(merged, 3))
        assertEquals(merged, roundTrip(sections, 3))
    }

    @Test
    fun allBlank() {
        assertTrue(TodayHubRowColumns.allBlank(listOf("", "  \n")))
        assertFalse(TodayHubRowColumns.allBlank(listOf("x")))
    }

    @Test
    fun normalizesCrlf() {
        assertEquals(listOf("a\n\nb"), TodayHubRowColumns.split("a\r\n\r\nb", 1))
    }
}
