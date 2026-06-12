package com.eskerra.go.core.inbox

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Mirrors `packages/eskerra-core/src/inbox/inboxTileColor.ts`. */
object InboxTileColor {

    const val NEUTRAL_GRAY = "#6b7280"

    private const val MS_PER_DAY = 86_400_000L

    private val BASE_HEX_BY_WEEKDAY = listOf(
        "#73C6B6", // Sunday
        "#5DADE2", // Monday
        "#58D68D", // Tuesday
        "#F4D03F", // Wednesday
        "#EB984E", // Thursday
        "#EC7063", // Friday
        "#AF7AC5" // Saturday
    )

    fun mixHex(baseHex: String, targetHex: String, t: Double): String {
        val clamped = t.coerceIn(0.0, 1.0)
        val (r0, g0, b0) = parseRgb(baseHex)
        val (r1, g1, b1) = parseRgb(targetHex)
        val r = kotlin.math.round(r0 * (1 - clamped) + r1 * clamped).toInt()
        val g = kotlin.math.round(g0 * (1 - clamped) + g1 * clamped).toInt()
        val b = kotlin.math.round(b0 * (1 - clamped) + b1 * clamped).toInt()
        return "#${r.toString(16).padStart(2, '0')}" +
            "${g.toString(16).padStart(2, '0')}" +
            "${b.toString(16).padStart(2, '0')}"
    }

    fun backgroundColor(
        lastModifiedEpochMillis: Long?,
        nowEpochMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (lastModifiedEpochMillis == null || lastModifiedEpochMillis > nowEpochMillis) {
            return NEUTRAL_GRAY
        }
        val ageMs = nowEpochMillis - lastModifiedEpochMillis
        val grayMix = grayMixRatioForAgeMs(ageMs)
        val base = weekdayBaseHex(lastModifiedEpochMillis, timeZone)
        return mixHex(base, NEUTRAL_GRAY, grayMix)
    }

    private fun grayMixRatioForAgeMs(ageMs: Long): Double {
        val ageDays = ageMs.toDouble() / MS_PER_DAY
        return when {
            ageDays < 7 -> 0.0
            ageDays < 14 -> 0.25
            ageDays < 21 -> 0.5
            ageDays < 28 -> 0.75
            else -> 1.0
        }
    }

    private fun weekdayBaseHex(lastModifiedMs: Long, timeZone: TimeZone): String {
        val dayOfWeek = Instant.fromEpochMilliseconds(lastModifiedMs)
            .toLocalDateTime(timeZone)
            .date
            .dayOfWeek
        val index = when (dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
        return BASE_HEX_BY_WEEKDAY[index]
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int> {
        val normalized = hex.removePrefix("#")
        require(normalized.length == 6) { "Expected #RRGGBB, got $hex" }
        return Triple(
            normalized.substring(0, 2).toInt(16),
            normalized.substring(2, 4).toInt(16),
            normalized.substring(4, 6).toInt(16)
        )
    }
}
