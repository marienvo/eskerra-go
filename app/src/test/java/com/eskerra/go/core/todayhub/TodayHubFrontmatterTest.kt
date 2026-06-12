package com.eskerra.go.core.todayhub

import com.eskerra.go.core.todayhub.TodayHubFrontmatter.StartDay
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seeded from `todayHub.test.ts` (`parseTodayHubFrontmatter`). */
class TodayHubFrontmatterTest {

    @Test
    fun defaultsWhenNoFrontmatter() {
        val s = TodayHubFrontmatter.parse("# Hello\n\nbody")
        assertEquals(TodayHubFrontmatter.PERPETUAL_TYPE_WEEKLY, s.perpetualType)
        assertEquals(emptyList<String>(), s.columns)
        assertEquals(StartDay.MONDAY, s.start)
    }

    @Test
    fun readsPerpetualTypeColumnsStart() {
        val md = """
            ---
            perpetualType: weekly
            columns:
              - Next actions
            start: monday
            ---
            # Today hub
        """.trimIndent()
        val s = TodayHubFrontmatter.parse(md)
        assertEquals(TodayHubFrontmatter.PERPETUAL_TYPE_WEEKLY, s.perpetualType)
        assertEquals(listOf("Next actions"), s.columns)
        assertEquals(StartDay.MONDAY, s.start)
        assertEquals(2, TodayHubFrontmatter.columnCount(s))
    }

    @Test
    fun readsStartCaseInsensitive() {
        val md = "---\nstart: Saturday\n---\n"
        assertEquals(StartDay.SATURDAY, TodayHubFrontmatter.parse(md).start)
    }

    @Test
    fun ignoresUnknownStartValue() {
        val md = "---\nstart: funday\n---\n"
        assertEquals(StartDay.MONDAY, TodayHubFrontmatter.parse(md).start)
    }

    @Test
    fun readsMultipleColumns() {
        val md = "---\ncolumns:\n  - A\n  - B\n---\n"
        val s = TodayHubFrontmatter.parse(md)
        assertEquals(listOf("A", "B"), s.columns)
        assertEquals(3, TodayHubFrontmatter.columnCount(s))
    }

    @Test
    fun readsSingleColumnScalarOnColumnsLine() {
        val md = "---\ncolumns: Next actions\nstart: monday\n---\n"
        val s = TodayHubFrontmatter.parse(md)
        assertEquals(listOf("Next actions"), s.columns)
        assertEquals(2, TodayHubFrontmatter.columnCount(s))
    }
}
