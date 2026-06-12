package com.eskerra.go.core.todayhub

import com.eskerra.go.core.todayhub.TodayHubFrontmatter.StartDay
import kotlinx.datetime.LocalDate

/**
 * Today hub week selection/navigation (spec §11.4). The hub opens on the current week
 * (`weekStarts[1]`) and prev/next jump only between weeks that exist on disk, with the current week
 * always reachable so an empty current week still renders. Pure; UI-free.
 */
object TodayHubNavigation {

    /** `YYYY-MM-DD` stem of the week containing [now] for a hub's configured [start] day. */
    fun currentWeekStem(now: LocalDate, start: StartDay): String =
        TodayHubWeeks.formatMondayStem(TodayHubWeeks.weekStartForDate(now, start))

    /**
     * Sorted (ascending) week stems the user can navigate between: the on-disk [availableStems]
     * plus [currentWeekStem]. De-duplicated so the current week is always present exactly once.
     */
    fun navigableWeekStems(availableStems: List<String>, currentWeekStem: String): List<String> =
        (availableStems + currentWeekStem).distinct().sorted()

    /** Stem [delta] steps from [current] within [stems], or `null` when out of range. */
    fun adjacentStem(stems: List<String>, current: String, delta: Int): String? {
        val index = stems.indexOf(current)
        if (index < 0) return null
        return stems.getOrNull(index + delta)
    }

    fun hasPrev(stems: List<String>, current: String): Boolean =
        adjacentStem(stems, current, -1) != null

    fun hasNext(stems: List<String>, current: String): Boolean =
        adjacentStem(stems, current, 1) != null
}
