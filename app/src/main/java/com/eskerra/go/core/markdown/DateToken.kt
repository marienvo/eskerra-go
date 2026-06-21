package com.eskerra.go.core.markdown

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reminder date-token grammar and read-only pill classification.
 *
 * Mirrors the read-only subset of
 * `apps/desktop/src/editor/noteEditor/dateToken/dateToken.ts`. The app renders pills only — no
 * picker/editing — so the token-snapping and default-time helpers are intentionally omitted.
 */
object DateToken {

    /** Parsed token value. `hour`/`minute` are null for a date-only token. */
    data class DateTokenValue(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int? = null,
        val minute: Int? = null,
        val struck: Boolean = false
    )

    /** Colour bucket for a reminder pill. */
    enum class PillTone { COMPLETED, PAST, URGENT, FUTURE, NEUTRAL }

    /** Part of the day a clock time falls in. Boundaries: 12:30 and 17:30. */
    enum class Daypart { MORNING, AFTERNOON, EVENING }

    /** Live token span (excludes the word-boundary whitespace prefix). Group 1 is the token. */
    val DATE_TOKEN_PATTERN = Regex("""(?:^|\s)(@\d{4}-\d{2}-\d{2}(?:_\d{4})?)""")

    /** Struck token on disk: `@~~YYYY-MM-DD(_HHMM)?~~`, with optional daemon `\_` escape. */
    val STRUCK_DATE_TOKEN_PATTERN = Regex("""(?:^|\s)(@~~\d{4}-\d{2}-\d{2}(?:\\?_\d{4})?~~)""")

    private val PARSE_RE = Regex("""^@(\d{4})-(\d{2})-(\d{2})(?:_(\d{4}))?$""")

    private val SHORT_WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val PRETTY_MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private const val DAYPART_AFTERNOON_START_MIN = 12 * 60 + 30
    private const val DAYPART_EVENING_START_MIN = 17 * 60 + 30

    fun pad2(value: Int): String = value.toString().padStart(2, '0')

    fun pad4(value: Int): String = value.toString().padStart(4, '0')

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int {
        val days =
            listOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return days[month - 1]
    }

    fun isValidCalendarDate(year: Int, month: Int, day: Int): Boolean {
        if (month < 1 || month > 12 || day < 1) {
            return false
        }
        return day <= daysInMonth(year, month)
    }

    private fun isValidTime(hour: Int, minute: Int): Boolean = hour in 0..23 && minute in 0..59

    /** Maps daemon `\_` escape noise to `_` inside a struck span before parsing. */
    fun normalizeStruckTimeSeparator(text: String): String = text.replace("\\_", "_")

    fun formatDateToken(value: DateTokenValue): String {
        val datePart = "${pad4(value.year)}-${pad2(value.month)}-${pad2(value.day)}"
        val timePart = if (value.hour != null && value.minute != null) {
            "_${pad2(value.hour)}${pad2(value.minute)}"
        } else {
            ""
        }
        if (value.struck) {
            return "@~~$datePart$timePart~~"
        }
        return "@$datePart$timePart"
    }

    fun parseDateToken(text: String): DateTokenValue? {
        val match = PARSE_RE.matchEntire(text) ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        if (!isValidCalendarDate(year, month, day)) {
            return null
        }
        val timeSuffix = match.groupValues[4]
        if (timeSuffix.isEmpty()) {
            return DateTokenValue(year, month, day)
        }
        val hour = timeSuffix.substring(0, 2).toInt()
        val minute = timeSuffix.substring(2, 4).toInt()
        if (!isValidTime(hour, minute)) {
            return null
        }
        return DateTokenValue(year, month, day, hour, minute)
    }

    /** Parses a live or struck full token span. */
    fun parseDateTokenSpan(span: String): DateTokenValue? {
        if (span.startsWith("@~~") && span.endsWith("~~")) {
            val inner = span.substring(3, span.length - 2)
            val normalized = normalizeStruckTimeSeparator(inner)
            val value = parseDateToken("@$normalized") ?: return null
            return value.copy(struck = true)
        }
        val value = parseDateToken(span) ?: return null
        return value.copy(struck = false)
    }

    /** A token located within a line of text. */
    data class TokenSpanInLine(
        val token: String,
        val tokenStartInLine: Int,
        val value: DateTokenValue
    )

    /**
     * Returns non-overlapping live and struck token spans on one line. Struck spans are collected
     * first so inner `@` digits never match as live tokens.
     */
    fun collectTokenSpansInLine(lineText: String): List<TokenSpanInLine> {
        val spans = mutableListOf<TokenSpanInLine>()
        val occupied = mutableListOf<IntRange>()

        for (match in STRUCK_DATE_TOKEN_PATTERN.findAll(lineText)) {
            val group = match.groups[1] ?: continue
            val value = parseDateTokenSpan(group.value) ?: continue
            spans += TokenSpanInLine(group.value, group.range.first, value)
            occupied += group.range
        }

        for (match in DATE_TOKEN_PATTERN.findAll(lineText)) {
            val group = match.groups[1] ?: continue
            val range = group.range
            val overlaps = occupied.any { it.first <= range.last && range.first <= it.last }
            if (overlaps) {
                continue
            }
            val value = parseDateTokenSpan(group.value) ?: continue
            spans += TokenSpanInLine(group.value, range.first, value)
        }

        return spans.sortedBy { it.tokenStartInLine }
    }

    /** Daypart for a minute-of-day value (0–1439). */
    fun daypartOfMinutes(minutesSinceMidnight: Int): Daypart = when {
        minutesSinceMidnight < DAYPART_AFTERNOON_START_MIN -> Daypart.MORNING
        minutesSinceMidnight < DAYPART_EVENING_START_MIN -> Daypart.AFTERNOON
        else -> Daypart.EVENING
    }

    private fun daypartLabel(daypart: Daypart): String = when (daypart) {
        Daypart.MORNING -> "This morning"
        Daypart.AFTERNOON -> "This afternoon"
        Daypart.EVENING -> "This evening"
    }

    private fun dateOf(value: DateTokenValue): LocalDate =
        LocalDate.of(value.year, value.month, value.day)

    private fun dateTimeOf(value: DateTokenValue): LocalDateTime =
        LocalDateTime.of(value.year, value.month, value.day, value.hour ?: 0, value.minute ?: 0)

    /**
     * Whether a token's moment has already passed. Timed tokens compare to the exact clock;
     * date-only tokens are past only once the whole day is behind us.
     */
    fun isDateTokenInPast(value: DateTokenValue, now: LocalDateTime): Boolean {
        if (value.hour != null && value.minute != null) {
            return dateTimeOf(value).isBefore(now)
        }
        return dateOf(value).isBefore(now.toLocalDate())
    }

    /** Whether the token's local date is strictly after today. */
    fun isDateTokenFuture(value: DateTokenValue, now: LocalDateTime): Boolean =
        dateOf(value).isAfter(now.toLocalDate())

    /**
     * Classifies a token into its pill colour bucket. A timed today reminder still to come is
     * `urgent` in the current daypart and `future` in a later one.
     */
    fun pillTone(value: DateTokenValue, now: LocalDateTime): PillTone {
        if (value.struck) {
            return PillTone.COMPLETED
        }
        if (isDateTokenInPast(value, now)) {
            return PillTone.PAST
        }
        if (isDateTokenFuture(value, now)) {
            return PillTone.FUTURE
        }
        if (value.hour != null && value.minute != null) {
            val reminderDaypart = daypartOfMinutes(value.hour * 60 + value.minute)
            val currentDaypart = daypartOfMinutes(now.hour * 60 + now.minute)
            return if (reminderDaypart == currentDaypart) PillTone.URGENT else PillTone.FUTURE
        }
        return PillTone.NEUTRAL
    }

    /** Human duration like `5 h 15 min`, dropping zero parts; under a minute reads as `now`. */
    fun formatDurationHm(totalMinutes: Double): String {
        val minutes = maxOf(0, totalMinutes.roundToInt())
        if (minutes < 1) {
            return "now"
        }
        val hours = minutes / 60
        val mins = minutes % 60
        val parts = mutableListOf<String>()
        if (hours > 0) {
            parts += "$hours h"
        }
        if (mins > 0) {
            parts += "$mins min"
        }
        return parts.joinToString(" ")
    }

    private fun prettyTimeSuffix(value: DateTokenValue): String {
        if (value.hour == null || value.minute == null) {
            return ""
        }
        return " at ${pad2(value.hour)}:${pad2(value.minute)}"
    }

    private fun prettyAbsoluteDate(value: DateTokenValue, now: LocalDateTime): String {
        val month = PRETTY_MONTHS[value.month - 1]
        val yearSuffix = if (value.year == now.year) "" else " ${value.year}"
        return "${value.day} $month$yearSuffix"
    }

    /**
     * Friendly label for a date token, without the bell. Future dates within two weeks render
     * relatively (Today / Tomorrow / weekday / "Next <Weekday>"); everything else is absolute.
     */
    fun formatDateTokenPretty(value: DateTokenValue, now: LocalDateTime): String {
        val diffDays = ChronoUnit.DAYS.between(now.toLocalDate(), dateOf(value)).toInt()

        if (diffDays == 0 && value.hour != null && value.minute != null) {
            return formatTodayTimedLabel(value, now)
        }

        val time = prettyTimeSuffix(value)
        val datePart = when {
            diffDays == 0 -> "Today"
            diffDays == 1 -> "Tomorrow"
            diffDays in 2..13 -> {
                val jsDay = dateOf(value).dayOfWeek.value % 7
                val weekday = SHORT_WEEKDAYS[jsDay]
                if (diffDays <= 7) weekday else "Next $weekday"
            }
            else -> prettyAbsoluteDate(value, now)
        }
        return "$datePart$time"
    }

    private fun formatTodayTimedLabel(value: DateTokenValue, now: LocalDateTime): String {
        val reminderMin = value.hour!! * 60 + value.minute!!
        val nowMin = now.hour * 60 + now.minute
        val reminderDaypart = daypartOfMinutes(reminderMin)
        val currentDaypart = daypartOfMinutes(nowMin)

        if (reminderDaypart != currentDaypart) {
            return "${daypartLabel(reminderDaypart)}${prettyTimeSuffix(value)}"
        }

        val absMin = abs(Duration.between(now, dateTimeOf(value)).toMillis()) / 60_000.0
        if (isDateTokenInPast(value, now)) {
            return if (absMin < 1) "just now" else "${formatDurationHm(absMin)} ago"
        }
        return if (absMin < 1) {
            "now (${pad2(value.hour)}:${pad2(value.minute)})"
        } else {
            "in ${formatDurationHm(absMin)} (${pad2(value.hour)}:${pad2(value.minute)})"
        }
    }
}
