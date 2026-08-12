package com.eskerra.go.core.repository

import com.eskerra.go.core.model.ColdStartSummary

/**
 * Sends an aggregated performance report to whatever observability backend is configured.
 *
 * Implementations must be silent no-ops when reporting is unavailable (no DSN configured, SDK not
 * initialised): a failure to report is never worth surfacing to the user or failing a launch over.
 */
interface PerformanceReporter {
    suspend fun reportWeeklyColdStart(summary: ColdStartSummary)
}
