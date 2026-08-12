package com.eskerra.go.core.perf

import com.eskerra.go.core.model.ColdStartSample
import com.eskerra.go.core.model.ColdStartSummary
import com.eskerra.go.core.model.PhaseStat

/** Turns raw cold-start samples into the aggregate that gets reported. Pure. */
object ColdStartAggregator {

    fun summarize(samples: List<ColdStartSample>, windowDays: Int): ColdStartSummary? {
        if (samples.isEmpty()) return null
        return ColdStartSummary(
            sampleCount = samples.size,
            windowDays = windowDays,
            total = samples.phaseStat { it.totalMs },
            processToActivity = samples.phaseStat { it.processToActivityMs },
            diBuild = samples.phaseStat { it.diBuildMs },
            toGateStart = samples.phaseStat { it.toGateStartMs },
            gateResolve = samples.phaseStat { it.gateResolveMs },
            snapshotRead = samples.phaseStat { it.snapshotReadMs },
            firstScan = samples.phaseStat { it.firstScanMs },
            settleTail = samples.phaseStat { it.settleTailMs },
            medianNoteCount = median(samples.map { it.noteCount.toLong() }).toInt(),
            fingerprintHitRate = samples.rateOf { it.fingerprintHit },
            snapshotHitRate = samples.rateOf { it.snapshotHit },
            memoBaseRate = samples.rateOf { it.hadMemoBase }
        )
    }

    private fun List<ColdStartSample>.phaseStat(select: (ColdStartSample) -> Long): PhaseStat {
        val values = map(select)
        return PhaseStat(medianMs = median(values), p90Ms = percentile(values, 90))
    }

    private fun List<ColdStartSample>.rateOf(predicate: (ColdStartSample) -> Boolean): Double =
        count(predicate).toDouble() / size

    /** Even counts average the two middle values, so a 2-sample window is still meaningful. */
    internal fun median(values: List<Long>): Long {
        require(values.isNotEmpty()) { "median of empty list" }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }

    /** Nearest-rank percentile: the smallest value at or above the requested rank. */
    internal fun percentile(values: List<Long>, percentile: Int): Long {
        require(values.isNotEmpty()) { "percentile of empty list" }
        val sorted = values.sorted()
        val rank = Math.ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
