package com.eskerra.go.data.observability

import com.eskerra.go.core.model.ColdStartSummary
import com.eskerra.go.core.model.PhaseStat
import com.eskerra.go.core.repository.PerformanceReporter
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Sends the weekly cold-start aggregate to Sentry as an `info` event.
 *
 * Contract documented in `specs/observability/README.md`. This is the only file outside
 * `EskerraGoApplication` allowed to import `io.sentry`, enforced by an ArchUnit rule.
 *
 * A no-op when Sentry never initialised — the normal local case, where `SENTRY_DSN` is empty.
 */
class SentryPerformanceReporter : PerformanceReporter {

    override suspend fun reportWeeklyColdStart(summary: ColdStartSummary) {
        if (!Sentry.isEnabled()) return
        runCatching {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.INFO
                // A fixed fingerprint keeps every weekly report in one issue, so the trend across
                // weeks reads as a single timeline instead of a new issue each time.
                scope.setFingerprint(listOf(FINGERPRINT))
                scope.setTag("perf.report", "cold_start_weekly")
                scope.setContexts("cold_start", summary.toContext())
                Sentry.captureMessage(MESSAGE)
            }
        }
    }

    /** Durations, counts and rates only — never note titles, paths, or remote URIs. */
    private fun ColdStartSummary.toContext(): Map<String, Any> = buildMap {
        put("sample_count", sampleCount)
        put("window_days", windowDays)
        putPhase("total", total)
        putPhase("process_to_activity", processToActivity)
        putPhase("di_build", diBuild)
        putPhase("to_gate_start", toGateStart)
        putPhase("gate_resolve", gateResolve)
        putPhase("snapshot_read", snapshotRead)
        putPhase("first_scan", firstScan)
        putPhase("settle_tail", settleTail)
        put("median_note_count", medianNoteCount)
        put("fingerprint_hit_rate", fingerprintHitRate)
        put("snapshot_hit_rate", snapshotHitRate)
        put("memo_base_rate", memoBaseRate)
    }

    private fun MutableMap<String, Any>.putPhase(name: String, stat: PhaseStat) {
        put("${name}_median_ms", stat.medianMs)
        put("${name}_p90_ms", stat.p90Ms)
    }

    companion object {
        const val MESSAGE = "perf.cold_start.weekly"
        const val FINGERPRINT = "perf-cold-start-weekly"
    }
}
