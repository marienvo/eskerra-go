package com.eskerra.go.core.repository

import com.eskerra.go.core.model.ColdStartSample

/**
 * Rolling window of cold-start samples awaiting their next report.
 *
 * Holds durations and counts only — never vault content — so the reporting window can be sent
 * off-device without leaking anything about the notes themselves.
 */
interface ColdStartStatsStore {

    /** Appends a sample, dropping the oldest once the window is full. */
    suspend fun record(sample: ColdStartSample)

    suspend fun readWindow(): List<ColdStartSample>

    /** Epoch millis of the last successful report; `null` before the first window closes. */
    suspend fun readLastReportedAtMs(): Long?

    /** Clears the window and stamps [reportedAtMs] as the start of the next one. */
    suspend fun resetWindow(reportedAtMs: Long)
}
