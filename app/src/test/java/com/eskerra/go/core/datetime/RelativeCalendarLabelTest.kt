package com.eskerra.go.core.datetime

import java.util.Calendar
import java.util.TimeZone
import kotlinx.datetime.TimeZone as KotlinTimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeCalendarLabelTest {

    private val timeZone = KotlinTimeZone.of(TimeZone.getDefault().id)
    private val now = localMillis(2020, Calendar.JANUARY, 22, 12, 0)

    @Test
    fun format_returnsEmDashWhenLastModifiedNull() {
        assertEquals(
            RelativeCalendarLabel.EM_DASH,
            RelativeCalendarLabel.format(null, now, timeZone)
        )
    }

    @Test
    fun format_returnsTodayForSameLocalDay() {
        val sameDay = localMillis(2020, Calendar.JANUARY, 22, 6, 0)
        assertEquals("Today", RelativeCalendarLabel.format(sameDay, now, timeZone))
    }

    @Test
    fun format_returnsYesterdayForPreviousLocalDay() {
        val previous = localMillis(2020, Calendar.JANUARY, 21, 18, 0)
        assertEquals("Yesterday", RelativeCalendarLabel.format(previous, now, timeZone))
    }

    @Test
    fun format_returnsWeekdayForTwoToSixDaysAgo() {
        val monday = localMillis(2020, Calendar.JANUARY, 20, 12, 0)
        assertEquals("Monday", RelativeCalendarLabel.format(monday, now, timeZone))
    }

    @Test
    fun format_returnsIsoDateAtSevenPlusDaysAgo() {
        val weekAgo = localMillis(2020, Calendar.JANUARY, 15, 12, 0)
        assertEquals("2020-01-15", RelativeCalendarLabel.format(weekAgo, now, timeZone))
    }

    @Test
    fun format_returnsIsoDateForFutureModification() {
        val future = localMillis(2020, Calendar.JANUARY, 25, 12, 0)
        assertEquals("2020-01-25", RelativeCalendarLabel.format(future, now, timeZone))
    }

    @Test
    fun formatFromIsoDate_returnsTodayForSameDate() {
        assertEquals("Today", RelativeCalendarLabel.formatFromIsoDate("2020-01-22", now, timeZone))
    }

    @Test
    fun formatFromIsoDate_returnsOriginalStringWhenInvalid() {
        assertEquals("bad-date", RelativeCalendarLabel.formatFromIsoDate("bad-date", now, timeZone))
    }

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
