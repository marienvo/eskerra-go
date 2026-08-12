package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.ColdStartSample
import com.eskerra.go.core.model.ColdStartSummary
import com.eskerra.go.core.repository.ColdStartStatsStore
import com.eskerra.go.core.repository.PerformanceReporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWeeklyPerformanceTest {

    private val weekMs = 7L * 24 * 60 * 60 * 1000

    @Test
    fun firstEverLaunchStartsTheWindowInsteadOfReporting() = runTest {
        val store = FakeStatsStore(lastReportedAtMs = null)
        val reporter = FakeReporter()

        val reported = report(store, reporter, nowMs = 1_000L)(sample())

        assertFalse(reported)
        assertNull(reporter.lastSummary)
        assertEquals(1_000L, store.lastReportedAtMs)
    }

    @Test
    fun doesNotReportBeforeTheWindowElapses() = runTest {
        val store = FakeStatsStore(lastReportedAtMs = 0L)
        repeat(10) { store.record(sample()) }
        val reporter = FakeReporter()

        val reported = report(store, reporter, nowMs = weekMs - 1)(sample())

        assertFalse(reported)
        assertNull(reporter.lastSummary)
    }

    @Test
    fun doesNotReportAWindowWithTooFewSamples() = runTest {
        val store = FakeStatsStore(lastReportedAtMs = 0L)
        repeat(4) { store.record(sample()) }
        val reporter = FakeReporter()

        val reported = report(store, reporter, nowMs = weekMs + 1)(sample = null)

        assertFalse(reported)
        assertNull(reporter.lastSummary)
        // The timestamp is untouched, so the next launch reconsiders rather than losing the week.
        assertEquals(0L, store.lastReportedAtMs)
    }

    @Test
    fun reportsAndResetsOnceTheWindowElapses() = runTest {
        val store = FakeStatsStore(lastReportedAtMs = 0L)
        repeat(4) { store.record(sample(totalMs = 3000)) }
        val reporter = FakeReporter()

        val reported = report(store, reporter, nowMs = weekMs + 1)(sample(totalMs = 5000))

        assertTrue(reported)
        assertEquals(5, reporter.lastSummary?.sampleCount)
        assertEquals(7, reporter.lastSummary?.windowDays)
        assertEquals(3000L, reporter.lastSummary?.total?.medianMs)
        assertEquals(weekMs + 1, store.lastReportedAtMs)
        assertTrue(store.readWindow().isEmpty())
    }

    @Test
    fun dropsUnusableLaunchesButStillEvaluatesTheWindow() = runTest {
        val store = FakeStatsStore(lastReportedAtMs = 0L)
        repeat(5) { store.record(sample()) }
        val reporter = FakeReporter()

        val reported = report(store, reporter, nowMs = weekMs + 1)(sample = null)

        assertTrue(reported)
        assertEquals(5, reporter.lastSummary?.sampleCount)
    }

    private fun report(store: ColdStartStatsStore, reporter: PerformanceReporter, nowMs: Long) =
        ReportWeeklyPerformance(store, reporter, now = { nowMs })

    private fun sample(totalMs: Long = 3269) = ColdStartSample(
        processToActivityMs = 618,
        diBuildMs = 155,
        toGateStartMs = 270,
        gateResolveMs = 114,
        snapshotReadMs = 743,
        firstScanMs = 367,
        settleTailMs = 608,
        totalMs = totalMs,
        noteCount = 1161,
        fingerprintHit = true,
        snapshotHit = true,
        hadMemoBase = true
    )

    private class FakeStatsStore(var lastReportedAtMs: Long?) : ColdStartStatsStore {
        private val window = mutableListOf<ColdStartSample>()

        override suspend fun record(sample: ColdStartSample) {
            window += sample
        }

        override suspend fun readWindow(): List<ColdStartSample> = window.toList()

        override suspend fun readLastReportedAtMs(): Long? = lastReportedAtMs

        override suspend fun resetWindow(reportedAtMs: Long) {
            window.clear()
            lastReportedAtMs = reportedAtMs
        }
    }

    private class FakeReporter : PerformanceReporter {
        var lastSummary: ColdStartSummary? = null

        override suspend fun reportWeeklyColdStart(summary: ColdStartSummary) {
            lastSummary = summary
        }
    }
}
