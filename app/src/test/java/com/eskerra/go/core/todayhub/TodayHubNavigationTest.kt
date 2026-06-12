package com.eskerra.go.core.todayhub

import com.eskerra.go.core.todayhub.TodayHubFrontmatter.StartDay
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayHubNavigationTest {

    @Test
    fun currentWeekStemUsesHubStartDay() {
        val wednesday = LocalDate(2026, 4, 8)
        assertEquals("2026-04-06", TodayHubNavigation.currentWeekStem(wednesday, StartDay.MONDAY))
        assertEquals("2026-04-04", TodayHubNavigation.currentWeekStem(wednesday, StartDay.SATURDAY))
    }

    @Test
    fun navigableStemsIncludeCurrentWeekSortedAndDeduplicated() {
        val stems = TodayHubNavigation.navigableWeekStems(
            availableStems = listOf("2026-04-06", "2026-03-30"),
            currentWeekStem = "2026-04-13"
        )
        assertEquals(listOf("2026-03-30", "2026-04-06", "2026-04-13"), stems)
    }

    @Test
    fun currentWeekNotDuplicatedWhenAlreadyOnDisk() {
        val stems = TodayHubNavigation.navigableWeekStems(
            availableStems = listOf("2026-04-06", "2026-04-13"),
            currentWeekStem = "2026-04-13"
        )
        assertEquals(listOf("2026-04-06", "2026-04-13"), stems)
    }

    @Test
    fun adjacentStemMovesWithinRange() {
        val stems = listOf("2026-03-30", "2026-04-06", "2026-04-13")
        assertEquals("2026-03-30", TodayHubNavigation.adjacentStem(stems, "2026-04-06", -1))
        assertEquals("2026-04-13", TodayHubNavigation.adjacentStem(stems, "2026-04-06", 1))
        assertNull(TodayHubNavigation.adjacentStem(stems, "2026-03-30", -1))
        assertNull(TodayHubNavigation.adjacentStem(stems, "2026-04-13", 1))
        assertNull(TodayHubNavigation.adjacentStem(stems, "missing", 1))
    }

    @Test
    fun hasPrevAndHasNextReflectBounds() {
        val stems = listOf("2026-03-30", "2026-04-06")
        assertFalse(TodayHubNavigation.hasPrev(stems, "2026-03-30"))
        assertTrue(TodayHubNavigation.hasNext(stems, "2026-03-30"))
        assertTrue(TodayHubNavigation.hasPrev(stems, "2026-04-06"))
        assertFalse(TodayHubNavigation.hasNext(stems, "2026-04-06"))
    }
}
