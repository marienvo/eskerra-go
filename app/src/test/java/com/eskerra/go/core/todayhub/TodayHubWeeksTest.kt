package com.eskerra.go.core.todayhub

import com.eskerra.go.core.todayhub.TodayHubFrontmatter.StartDay
import com.eskerra.go.core.todayhub.TodayHubWeeks.SegmentKind
import com.eskerra.go.core.todayhub.TodayHubWeeks.WeekProgress
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seeded from `todayHub.test.ts` week math. JS month N maps to month N+1 here. */
class TodayHubWeeksTest {

    @Test
    fun addLocalCalendarDays() {
        val d = LocalDate(2026, 4, 13)
        assertEquals(LocalDate(2026, 4, 6), TodayHubWeeks.addLocalCalendarDays(d, -7))
        assertEquals(LocalDate(2026, 4, 20), TodayHubWeeks.addLocalCalendarDays(d, 7))
    }

    @Test
    fun startOfLocalWeekMonday() {
        assertEquals(
            LocalDate(2026, 4, 6),
            TodayHubWeeks.startOfLocalWeek(LocalDate(2026, 4, 7), 1)
        )
        assertEquals(
            LocalDate(2026, 4, 6),
            TodayHubWeeks.startOfLocalWeek(LocalDate(2026, 4, 6), 1)
        )
        assertEquals(
            LocalDate(2026, 3, 30),
            TodayHubWeeks.startOfLocalWeek(LocalDate(2026, 4, 5), 1)
        )
    }

    @Test
    fun startOfLocalWeekSaturday() {
        assertEquals(
            LocalDate(2026, 4, 4),
            TodayHubWeeks.startOfLocalWeek(LocalDate(2026, 4, 7), 6)
        )
    }

    @Test
    fun enumerateWeekStartsMonday() {
        val mondays = TodayHubWeeks.enumerateWeekStarts(LocalDate(2026, 4, 7), StartDay.MONDAY)
        assertEquals(53, mondays.size)
        assertEquals("2026-03-30", TodayHubWeeks.formatMondayStem(mondays[0]))
        assertEquals("2026-04-06", TodayHubWeeks.formatMondayStem(mondays[1]))
        assertEquals("2027-03-29", TodayHubWeeks.formatMondayStem(mondays[52]))
    }

    @Test
    fun enumerateWeekStartsSaturday() {
        val starts = TodayHubWeeks.enumerateWeekStarts(LocalDate(2026, 4, 7), StartDay.SATURDAY)
        assertEquals(53, starts.size)
        assertEquals("2026-03-28", TodayHubWeeks.formatMondayStem(starts[0]))
        assertEquals("2026-04-04", TodayHubWeeks.formatMondayStem(starts[1]))
        assertEquals("2027-03-27", TodayHubWeeks.formatMondayStem(starts[52]))
    }

    @Test
    fun weekEndInclusive() {
        assertEquals(LocalDate(2026, 4, 12), TodayHubWeeks.weekEndInclusive(LocalDate(2026, 4, 6)))
    }

    @Test
    fun parseRowStemRoundTrips() {
        assertEquals(LocalDate(2026, 4, 6), TodayHubWeeks.parseRowStem("2026-04-06"))
        assertNull(TodayHubWeeks.parseRowStem("2026-13-01"))
        assertNull(TodayHubWeeks.parseRowStem("not-a-date"))
    }

    @Test
    fun weekProgress() {
        val weekStart = LocalDate(2026, 4, 6)
        assertEquals(
            WeekProgress.Future,
            TodayHubWeeks.weekProgress(weekStart, LocalDate(2026, 4, 5))
        )
        assertEquals(
            WeekProgress.Current(0),
            TodayHubWeeks.weekProgress(weekStart, LocalDate(2026, 4, 6))
        )
        assertEquals(
            WeekProgress.Current(3),
            TodayHubWeeks.weekProgress(weekStart, LocalDate(2026, 4, 9))
        )
        assertEquals(
            WeekProgress.Current(6),
            TodayHubWeeks.weekProgress(weekStart, LocalDate(2026, 4, 12))
        )
        assertEquals(
            WeekProgress.Past,
            TodayHubWeeks.weekProgress(weekStart, LocalDate(2026, 4, 13))
        )
    }

    @Test
    fun weekendMergePair() {
        assertEquals(
            TodayHubWeeks.WeekendMergePair(5, 6),
            TodayHubWeeks.weekendMergePair(LocalDate(2026, 4, 6))
        )
        assertNull(TodayHubWeeks.weekendMergePair(LocalDate(2026, 4, 5)))
    }

    @Test
    fun weekendSegmentState() {
        val weekStart = LocalDate(2026, 4, 6)
        val state = { now: LocalDate -> TodayHubWeeks.weekendSegmentState(weekStart, now) }
        assertNull(TodayHubWeeks.weekendSegmentState(LocalDate(2026, 4, 5), LocalDate(2026, 4, 10)))
        assertEquals("future", state(LocalDate(2026, 4, 10)))
        assertEquals("current", state(LocalDate(2026, 4, 11)))
        assertEquals("current", state(LocalDate(2026, 4, 12)))
        assertEquals("past", state(LocalDate(2026, 4, 13)))
    }

    @Test
    fun weekProgressSegmentsSevenWhenNotMerged() {
        val weekStart = LocalDate(2026, 4, 5)
        val now = LocalDate(2026, 4, 7)
        val progress = TodayHubWeeks.weekProgress(weekStart, now)
        val segs = TodayHubWeeks.weekProgressSegments(progress, weekStart, now, 10, 3)
        assertEquals(7, segs.size)
        assertTrue(segs.all { it.widthPx == 10 })
    }

    @Test
    fun weekProgressSegmentsSixWithWideWeekend() {
        val weekStart = LocalDate(2026, 4, 6)
        val now = LocalDate(2026, 4, 11)
        val progress = TodayHubWeeks.weekProgress(weekStart, now)
        val segs = TodayHubWeeks.weekProgressSegments(progress, weekStart, now, 10, 3)
        assertEquals(6, segs.size)
        val wide = segs.first { it.dayIndex == null }
        assertEquals(10 * 2 + 3, wide.widthPx)
        assertEquals(SegmentKind.CURRENT, wide.kind)
    }
}
