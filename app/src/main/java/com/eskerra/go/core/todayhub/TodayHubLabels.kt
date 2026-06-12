package com.eskerra.go.core.todayhub

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * Today hub date labels (spec §11.4–§11.5). Mirrors `apps/mobile/.../todayHubFormat.ts`
 * (`formatTodayHubWeekDateLong`, `formatTodayHubWeekRangeShort`).
 */
object TodayHubLabels {

    private val LONG = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    /** Long-form week-start date for the date column header (e.g. `Monday, April 6, 2026`). */
    fun weekDateLong(weekStart: LocalDate): String = weekStart.toJavaLocalDate().format(LONG)

    /** Short week range for the navigation subtitle (e.g. `Apr 6 – Apr 12, 2026`). */
    fun weekRangeShort(weekStart: LocalDate): String {
        val end = TodayHubWeeks.weekEndInclusive(weekStart)
        val startPart = weekStart.toJavaLocalDate().format(MONTH_DAY)
        val endPart = end.toJavaLocalDate().format(MONTH_DAY_YEAR)
        return "$startPart – $endPart"
    }
}
