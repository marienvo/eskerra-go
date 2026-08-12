package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.ColdStartSample
import com.eskerra.go.core.perf.ColdStartAggregator
import com.eskerra.go.core.repository.ColdStartStatsStore
import com.eskerra.go.core.repository.PerformanceReporter

/**
 * Records the cold start that just finished and, once a week has passed, reports the window.
 *
 * There is no scheduler in this app (no WorkManager or AlarmManager, by design), so "weekly" is a
 * check performed on launch: the first launch after the window elapses carries the report.
 */
class ReportWeeklyPerformance(
    private val statsStore: ColdStartStatsStore,
    private val reporter: PerformanceReporter,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Returns true when a report was sent. [sample] is null for launches that were not
     * comparable cold starts.
     */
    suspend operator fun invoke(sample: ColdStartSample?): Boolean {
        sample?.let { statsStore.record(it) }

        val nowMs = now()
        val lastReportedAtMs = statsStore.readLastReportedAtMs()
        if (lastReportedAtMs == null) {
            // First ever launch: start the window rather than reporting a single sample as if it
            // were a week's worth of data.
            statsStore.resetWindow(nowMs)
            return false
        }
        if (nowMs - lastReportedAtMs < WINDOW_MS) {
            return false
        }

        val window = statsStore.readWindow()
        if (window.size < MIN_SAMPLES) {
            // Too little traffic to say anything. Keep collecting rather than reporting noise,
            // and leave the timestamp alone so the next launch reconsiders.
            return false
        }

        val summary = ColdStartAggregator.summarize(window, windowDays = WINDOW_DAYS)
            ?: return false
        reporter.reportWeeklyColdStart(summary)
        statsStore.resetWindow(nowMs)
        return true
    }

    companion object {
        const val WINDOW_DAYS = 7
        const val MIN_SAMPLES = 5
        private const val WINDOW_MS = WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }
}
