package com.eskerra.go.core.todayhub

import com.eskerra.go.core.todayhub.TodayHubFrontmatter.StartDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Today hub week math (spec §11.3–§11.5). Mirrors
 * `packages/eskerra-core/src/todayHub/todayHubMondays.ts`, expressed over local calendar
 * [LocalDate] values so day arithmetic is timezone-/DST-independent.
 */
object TodayHubWeeks {

    private val ROW_STEM_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** JS-style weekday number for a date (Sunday = 0 … Saturday = 6). */
    private fun jsWeekday(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun addLocalCalendarDays(date: LocalDate, deltaDays: Int): LocalDate =
        date.plus(deltaDays, DateTimeUnit.DAY)

    /** First day of the week containing [reference], using a JS [startDayJs] weekday (0..6). */
    fun startOfLocalWeek(reference: LocalDate, startDayJs: Int): LocalDate {
        val day = jsWeekday(reference)
        val diff = -((day - startDayJs + 7) % 7)
        return addLocalCalendarDays(reference, diff)
    }

    /** Week-start date of the week containing [date] for a hub's configured [start] day. */
    fun weekStartForDate(date: LocalDate, start: StartDay): LocalDate =
        startOfLocalWeek(date, start.jsDay)

    /**
     * 53 consecutive week-start dates: the previous week's anchor, then +7 days each step.
     * Row files use `YYYY-MM-DD` of each anchor day.
     */
    fun enumerateWeekStarts(now: LocalDate, start: StartDay): List<LocalDate> {
        val thisWeekStart = startOfLocalWeek(now, start.jsDay)
        val anchor = addLocalCalendarDays(thisWeekStart, -7)
        return (0 until 53).map { k -> addLocalCalendarDays(anchor, k * 7) }
    }

    /** Inclusive last calendar day of the week that begins on [weekStart] (seven-day span). */
    fun weekEndInclusive(weekStart: LocalDate): LocalDate = addLocalCalendarDays(weekStart, 6)

    /** `YYYY-MM-DD` row filename stem (week's first day). */
    fun formatMondayStem(weekStart: LocalDate): String {
        val mo = weekStart.monthNumber.toString().padStart(2, '0')
        val da = weekStart.dayOfMonth.toString().padStart(2, '0')
        return "${weekStart.year}-$mo-$da"
    }

    /** Parses a `YYYY-MM-DD` row stem into a [LocalDate], or `null` when not a valid calendar date. */
    fun parseRowStem(stem: String): LocalDate? {
        if (!ROW_STEM_RE.matches(stem)) return null
        val parsed = runCatching { LocalDate.parse(stem) }.getOrNull() ?: return null
        // Reject inputs that parse but normalize differently (defensive; LocalDate.parse is strict).
        return if (formatMondayStem(parsed) == stem) parsed else null
    }

    /** Progress of `now` within the 7-day window starting at [weekStart]. */
    sealed interface WeekProgress {
        data object Past : WeekProgress
        data class Current(val dayIndex: Int) : WeekProgress
        data object Future : WeekProgress
    }

    fun weekProgress(weekStart: LocalDate, now: LocalDate): WeekProgress {
        val diffDays = weekStart.daysUntil(now)
        return when {
            diffDays < 0 -> WeekProgress.Future
            diffDays > 6 -> WeekProgress.Past
            else -> WeekProgress.Current(diffDays)
        }
    }

    /** Index 0..6 in the hub week window whose local day matches [jsWeekdayValue], or `null`. */
    fun weekDayIndexForJsWeekday(weekStart: LocalDate, jsWeekdayValue: Int): Int? {
        for (i in 0 until 7) {
            if (jsWeekday(addLocalCalendarDays(weekStart, i)) == jsWeekdayValue) return i
        }
        return null
    }

    /** Adjacent Saturday/Sunday indices in the week strip, or `null` when they are not consecutive. */
    data class WeekendMergePair(val satIndex: Int, val sunIndex: Int)

    fun weekendMergePair(weekStart: LocalDate): WeekendMergePair? {
        val iSat = weekDayIndexForJsWeekday(weekStart, 6) ?: return null
        val iSun = weekDayIndexForJsWeekday(weekStart, 0) ?: return null
        return if (iSun == iSat + 1) WeekendMergePair(iSat, iSun) else null
    }

    /** Single past/current/future state for a merged Sat–Sun pair, or `null` when not merged. */
    fun weekendSegmentState(weekStart: LocalDate, now: LocalDate): String? {
        val merge = weekendMergePair(weekStart) ?: return null
        val sat = addLocalCalendarDays(weekStart, merge.satIndex)
        val sun = addLocalCalendarDays(weekStart, merge.sunIndex)
        return when {
            now < sat -> "future"
            now > sun -> "past"
            else -> "current"
        }
    }

    enum class SegmentKind { FILLED, CURRENT, EMPTY }

    data class ProgressSegment(
        val key: String,
        /** Hub day index 0..6, or `null` for a merged Sat–Sun block. */
        val dayIndex: Int?,
        val kind: SegmentKind,
        val widthPx: Int
    )

    private fun daySegmentKind(progress: WeekProgress, dayIndex: Int): SegmentKind =
        when (progress) {
            WeekProgress.Past -> SegmentKind.FILLED
            WeekProgress.Future -> SegmentKind.EMPTY
            is WeekProgress.Current -> when {
                dayIndex < progress.dayIndex -> SegmentKind.FILLED
                dayIndex == progress.dayIndex -> SegmentKind.CURRENT
                else -> SegmentKind.EMPTY
            }
        }

    /**
     * Row of progress segments: 7 narrow cells, or 6 with a wide merged weekend when Sat/Sun are
     * adjacent. Merged weekend width is `2 * cellPx + gapPx`.
     */
    fun weekProgressSegments(
        progress: WeekProgress,
        weekStart: LocalDate,
        now: LocalDate,
        cellPx: Int,
        gapPx: Int
    ): List<ProgressSegment> {
        val merge = weekendMergePair(weekStart)
            ?: return (0 until 7).map { i ->
                ProgressSegment("d$i", i, daySegmentKind(progress, i), cellPx)
            }
        val widePx = cellPx * 2 + gapPx
        val weekendKind = when (weekendSegmentState(weekStart, now)) {
            "past" -> SegmentKind.FILLED
            "current" -> SegmentKind.CURRENT
            else -> SegmentKind.EMPTY
        }
        val out = mutableListOf<ProgressSegment>()
        for (i in 0 until merge.satIndex) {
            out += ProgressSegment("d$i", i, daySegmentKind(progress, i), cellPx)
        }
        out += ProgressSegment("we", null, weekendKind, widePx)
        for (i in (merge.sunIndex + 1) until 7) {
            out += ProgressSegment("d$i", i, daySegmentKind(progress, i), cellPx)
        }
        return out
    }
}
