package com.eskerra.go.core.inbox

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxTileColorTest {

    private val timeZone = TimeZone.getDefault()

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun mixHex_returnsBaseWhenTIsZero() {
        assertEquals("#5dade2", InboxTileColor.mixHex("#5DADE2", InboxTileColor.NEUTRAL_GRAY, 0.0))
    }

    @Test
    fun mixHex_returnsTargetWhenTIsOne() {
        assertEquals("#6b7280", InboxTileColor.mixHex("#5DADE2", InboxTileColor.NEUTRAL_GRAY, 1.0))
    }

    @Test
    fun mixHex_clampsTOutsideRange() {
        assertEquals("#0000ff", InboxTileColor.mixHex("#ff0000", "#0000ff", 2.0))
        assertEquals("#ff0000", InboxTileColor.mixHex("#ff0000", "#0000ff", -1.0))
    }

    @Test
    fun backgroundColor_returnsNeutralGrayWhenLastModifiedNull() {
        val now = localMillis(2020, Calendar.JANUARY, 22, 12, 0)
        assertEquals(
            InboxTileColor.NEUTRAL_GRAY,
            InboxTileColor.backgroundColor(null, now, kotlinx.datetime.TimeZone.of(timeZone.id))
        )
    }

    @Test
    fun backgroundColor_returnsNeutralGrayWhenLastModifiedInFuture() {
        val now = localMillis(2020, Calendar.JANUARY, 22, 12, 0)
        val future = now + MS_PER_DAY
        assertEquals(
            InboxTileColor.NEUTRAL_GRAY,
            InboxTileColor.backgroundColor(future, now, kotlinx.datetime.TimeZone.of(timeZone.id))
        )
    }

    @Test
    fun backgroundColor_usesAgeUnderSevenDaysWithNoGrayMix() {
        val mondayNoon = localMillis(2020, Calendar.JANUARY, 20, 12, 0)
        val justBeforeSevenDays = mondayNoon + 7 * MS_PER_DAY - 1_000
        assertEquals(
            "#5dade2",
            InboxTileColor.backgroundColor(
                mondayNoon,
                justBeforeSevenDays,
                kotlinx.datetime.TimeZone.of(timeZone.id)
            )
        )
    }

    @Test
    fun backgroundColor_appliesTwentyFivePercentGrayAtSevenDays() {
        val mondayNoon = localMillis(2020, Calendar.JANUARY, 20, 12, 0)
        val atSevenDays = mondayNoon + 7 * MS_PER_DAY
        assertEquals(
            InboxTileColor.mixHex("#5DADE2", InboxTileColor.NEUTRAL_GRAY, 0.25),
            InboxTileColor.backgroundColor(
                mondayNoon,
                atSevenDays,
                kotlinx.datetime.TimeZone.of(timeZone.id)
            )
        )
    }

    @Test
    fun backgroundColor_returnsFullNeutralGrayAtTwentyEightDays() {
        val mondayNoon = localMillis(2020, Calendar.JANUARY, 20, 12, 0)
        val at28Days = mondayNoon + 28 * MS_PER_DAY
        assertEquals(
            InboxTileColor.NEUTRAL_GRAY,
            InboxTileColor.backgroundColor(
                mondayNoon,
                at28Days,
                kotlinx.datetime.TimeZone.of(timeZone.id)
            )
        )
    }

    companion object {
        private const val MS_PER_DAY = 86_400_000L
    }
}
