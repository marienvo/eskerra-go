package com.eskerra.go

import android.content.Context
import com.eskerra.go.core.usecase.ReportWeeklyPerformance
import com.eskerra.go.data.observability.SentryPerformanceReporter
import com.eskerra.go.data.perf.DataStoreColdStartStatsStore

/**
 * Builds the weekly performance reporting chain.
 *
 * Lifted out of [MainActivity] to keep that file inside its size budget, the same reason
 * [buildPodcastCompositionRoot] exists.
 */
fun buildWeeklyPerformanceReporting(context: Context): ReportWeeklyPerformance =
    ReportWeeklyPerformance(
        statsStore = DataStoreColdStartStatsStore(context),
        reporter = SentryPerformanceReporter()
    )
