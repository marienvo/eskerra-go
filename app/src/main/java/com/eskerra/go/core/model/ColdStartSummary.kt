package com.eskerra.go.core.model

/** Median and p90 for one launch phase across a reporting window. */
data class PhaseStat(val medianMs: Long, val p90Ms: Long)

/**
 * Aggregated cold-start behaviour over a reporting window.
 *
 * Median and p90 rather than a mean: launch times are skewed, and a mean hides whether a slow
 * median is everyone being a bit slow or a few very bad launches.
 */
data class ColdStartSummary(
    val sampleCount: Int,
    val windowDays: Int,
    val total: PhaseStat,
    val processToActivity: PhaseStat,
    val diBuild: PhaseStat,
    val toGateStart: PhaseStat,
    val gateResolve: PhaseStat,
    val snapshotRead: PhaseStat,
    val firstScan: PhaseStat,
    val settleTail: PhaseStat,
    val medianNoteCount: Int,
    /** Share of launches in [0.0, 1.0] where the workspace fingerprint still matched. */
    val fingerprintHitRate: Double,
    val snapshotHitRate: Double,
    val memoBaseRate: Double
)
