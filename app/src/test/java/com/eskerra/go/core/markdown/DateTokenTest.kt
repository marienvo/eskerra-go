package com.eskerra.go.core.markdown

import com.eskerra.go.core.markdown.DateToken.DateTokenValue
import com.eskerra.go.core.markdown.DateToken.Daypart
import com.eskerra.go.core.markdown.DateToken.PillTone
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vectors ported from `apps/desktop/src/editor/noteEditor/dateToken/dateToken.test.ts`.
 * JS `new Date(year, monthIndex, day, h, m)` maps to `LocalDateTime.of(year, monthIndex + 1, day, h, m)`.
 */
class DateTokenTest {

    // --- pad / calendar ---

    @Test
    fun pad2_zeroFills() {
        assertEquals("00", DateToken.pad2(0))
        assertEquals("06", DateToken.pad2(6))
        assertEquals("28", DateToken.pad2(28))
    }

    @Test
    fun pad4_zeroFills() {
        assertEquals("0000", DateToken.pad4(0))
        assertEquals("0952", DateToken.pad4(952))
        assertEquals("2352", DateToken.pad4(2352))
    }

    @Test
    fun isValidCalendarDate_acceptsOrdinary() {
        assert(DateToken.isValidCalendarDate(2026, 6, 6))
        assert(DateToken.isValidCalendarDate(2026, 12, 31))
    }

    @Test
    fun isValidCalendarDate_rejectsInvalid() {
        assert(!DateToken.isValidCalendarDate(2026, 0, 1))
        assert(!DateToken.isValidCalendarDate(2026, 13, 1))
        assert(!DateToken.isValidCalendarDate(2026, 6, 0))
        assert(!DateToken.isValidCalendarDate(2026, 6, 32))
    }

    @Test
    fun isValidCalendarDate_leapYearFeb29() {
        assert(DateToken.isValidCalendarDate(2028, 2, 29))
        assert(!DateToken.isValidCalendarDate(2026, 2, 29))
        assert(!DateToken.isValidCalendarDate(1900, 2, 29))
        assert(DateToken.isValidCalendarDate(2000, 2, 29))
    }

    // --- parse / format ---

    @Test
    fun parse_dateOnly() {
        assertEquals(DateTokenValue(2025, 12, 20), DateToken.parseDateToken("@2025-12-20"))
    }

    @Test
    fun parse_timed() {
        assertEquals(
            DateTokenValue(2025, 12, 20, 17, 0),
            DateToken.parseDateToken("@2025-12-20_1700")
        )
    }

    @Test
    fun parse_rejectsInvalidTime() {
        assertNull(DateToken.parseDateToken("@2025-12-20_2460"))
    }

    @Test
    fun parseSpan_struckSetsFlag() {
        assertEquals(
            DateTokenValue(2025, 12, 20, 17, 0, struck = true),
            DateToken.parseDateTokenSpan("@~~2025-12-20_1700~~")
        )
    }

    @Test
    fun parseSpan_struckAcceptsBackslashEscape() {
        assertEquals(
            DateTokenValue(2025, 12, 20, 17, 0, struck = true),
            DateToken.parseDateTokenSpan("@~~2025-12-20\\_1700~~")
        )
    }

    @Test
    fun format_roundTrips() {
        assertEquals("@2025-12-20", DateToken.formatDateToken(DateTokenValue(2025, 12, 20)))
        assertEquals(
            "@2025-12-20_1700",
            DateToken.formatDateToken(DateTokenValue(2025, 12, 20, 17, 0))
        )
        assertEquals(
            "@~~2025-12-20_1700~~",
            DateToken.formatDateToken(DateTokenValue(2025, 12, 20, 17, 0, struck = true))
        )
    }

    // --- collectTokenSpansInLine ---

    @Test
    fun collect_findsLiveTokensWithPositions() {
        val spans = DateToken.collectTokenSpansInLine("do x @2025-12-20 and @2025-12-21_0900")
        assertEquals(listOf("@2025-12-20", "@2025-12-21_0900"), spans.map { it.token })
        assertEquals(5, spans[0].tokenStartInLine)
    }

    @Test
    fun collect_struckTokenDoesNotAlsoMatchAsLive() {
        val spans = DateToken.collectTokenSpansInLine("done @~~2025-12-20_1700~~ ok")
        assertEquals(1, spans.size)
        assertEquals("@~~2025-12-20_1700~~", spans[0].token)
        assert(spans[0].value.struck)
    }

    // --- dateTokenPillTone (now = 2026-06-06 14:30, afternoon) ---

    private val afternoonNow = LocalDateTime.of(2026, 6, 6, 14, 30)

    @Test
    fun tone_completedAndPast() {
        assertEquals(
            PillTone.COMPLETED,
            DateToken.pillTone(DateTokenValue(2026, 6, 7, 9, 0, struck = true), afternoonNow)
        )
        assertEquals(PillTone.PAST, DateToken.pillTone(DateTokenValue(2026, 6, 5), afternoonNow))
        assertEquals(
            PillTone.PAST,
            DateToken.pillTone(DateTokenValue(2026, 6, 6, 9, 0), afternoonNow)
        )
    }

    @Test
    fun tone_currentDaypartTimedIsUrgent() {
        assertEquals(
            PillTone.URGENT,
            DateToken.pillTone(DateTokenValue(2026, 6, 6, 17, 0), afternoonNow)
        )
    }

    @Test
    fun tone_laterDaypartAndFutureDaysAreFuture() {
        assertEquals(
            PillTone.FUTURE,
            DateToken.pillTone(DateTokenValue(2026, 6, 6, 18, 0), afternoonNow)
        )
        assertEquals(PillTone.FUTURE, DateToken.pillTone(DateTokenValue(2026, 6, 8), afternoonNow))
    }

    @Test
    fun tone_dateOnlyTodayIsNeutral() {
        assertEquals(PillTone.NEUTRAL, DateToken.pillTone(DateTokenValue(2026, 6, 6), afternoonNow))
    }

    // --- daypart boundaries ---

    @Test
    fun daypart_boundaries() {
        assertEquals(Daypart.MORNING, DateToken.daypartOfMinutes(12 * 60 + 29))
        assertEquals(Daypart.AFTERNOON, DateToken.daypartOfMinutes(12 * 60 + 30))
        assertEquals(Daypart.AFTERNOON, DateToken.daypartOfMinutes(17 * 60 + 29))
        assertEquals(Daypart.EVENING, DateToken.daypartOfMinutes(17 * 60 + 30))
    }

    // --- formatDateTokenPretty (now = 2026-06-06 12:00, Saturday morning) ---

    private val noonNow = LocalDateTime.of(2026, 6, 6, 12, 0)

    @Test
    fun pretty_todayAndTomorrow() {
        assertEquals("Today", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6), noonNow))
        assertEquals(
            "Tomorrow",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 7), noonNow)
        )
    }

    @Test
    fun pretty_firstOccurrenceUsesBareWeekday() {
        assertEquals("Mon", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 8), noonNow))
        assertEquals("Thu", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 11), noonNow))
        assertEquals("Sat", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 13), noonNow))
    }

    @Test
    fun pretty_secondOccurrenceUsesNextWeekday() {
        assertEquals(
            "Next Sun",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 14), noonNow)
        )
        assertEquals(
            "Next Mon",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 15), noonNow)
        )
    }

    @Test
    fun pretty_beyondWindowIsAbsolute() {
        assertEquals(
            "20 Jun",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 20), noonNow)
        )
        assertEquals(
            "28 Dec",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 12, 28), noonNow)
        )
    }

    @Test
    fun pretty_pastDatesAreAbsolute() {
        assertEquals("5 Jun", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 5), noonNow))
    }

    @Test
    fun pretty_absoluteIncludesYearWhenNotCurrent() {
        assertEquals(
            "15 Jan 2027",
            DateToken.formatDateTokenPretty(DateTokenValue(2027, 1, 15), noonNow)
        )
    }

    @Test
    fun pretty_appendsTimeWhenPresent() {
        assertEquals(
            "Thu at 15:00",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 11, 15, 0), noonNow)
        )
        assertEquals("Thu", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 11), noonNow))
    }

    // --- timed today labels (dayparts) ---

    @Test
    fun pretty_currentDaypartUpcomingReadsRelative() {
        assertEquals(
            "in 15 min (12:15)",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6, 12, 15), noonNow)
        )
    }

    @Test
    fun pretty_currentDaypartPassedReadsAgo() {
        assertEquals(
            "2 h 55 min ago",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6, 9, 5), noonNow)
        )
    }

    @Test
    fun pretty_passedDaypartReadsItsName() {
        val afternoon = LocalDateTime.of(2026, 6, 6, 14, 30)
        assertEquals(
            "This morning at 09:05",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6, 9, 5), afternoon)
        )
    }

    @Test
    fun pretty_laterDaypartReadsItsName() {
        assertEquals(
            "This evening at 18:00",
            DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6, 18, 0), noonNow)
        )
    }

    @Test
    fun pretty_dateOnlyTodayKeepsTodayLabel() {
        assertEquals("Today", DateToken.formatDateTokenPretty(DateTokenValue(2026, 6, 6), noonNow))
    }
}
