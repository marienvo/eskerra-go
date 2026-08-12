package com.eskerra.go.core.perf

import com.eskerra.go.core.model.ColdStartSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColdStartAggregatorTest {

    @Test
    fun emptyWindowProducesNoSummary() {
        assertNull(ColdStartAggregator.summarize(emptyList(), windowDays = 7))
    }

    @Test
    fun medianOfOddCountIsTheMiddleValue() {
        assertEquals(3500L, ColdStartAggregator.median(listOf(3388, 4224, 3100, 5000, 3500)))
    }

    @Test
    fun medianOfEvenCountAveragesTheMiddlePair() {
        assertEquals(25L, ColdStartAggregator.median(listOf(10, 20, 30, 41)))
    }

    @Test
    fun medianOfSingleSampleIsThatSample() {
        assertEquals(42L, ColdStartAggregator.median(listOf(42)))
    }

    @Test
    fun p90PicksTheNearestRankNotAnInterpolation() {
        val values = (1L..10L).toList()
        assertEquals(9L, ColdStartAggregator.percentile(values, 90))
        assertEquals(10L, ColdStartAggregator.percentile(values, 100))
    }

    @Test
    fun p90OfSingleSampleIsThatSample() {
        assertEquals(7L, ColdStartAggregator.percentile(listOf(7), 90))
    }

    /** Mirrors the recorded baseline: 7 cold runs, median 3269ms, worst 4962ms. */
    @Test
    fun summarizesEachPhaseAndTheHitRates() {
        val totals = listOf(4962L, 3269L, 3375L, 4309L, 3200L, 3121L, 3119L)
        val samples = totals.mapIndexed { index, total ->
            sample(
                total = total,
                snapshotRead = total / 4,
                // Two of the seven launches missed the workspace fingerprint.
                fingerprintHit = index !in setOf(0, 2)
            )
        }

        val summary = ColdStartAggregator.summarize(samples, windowDays = 7)!!

        assertEquals(7, summary.sampleCount)
        assertEquals(7, summary.windowDays)
        assertEquals(3269L, summary.total.medianMs)
        assertEquals(4962L, summary.total.p90Ms)
        assertEquals(817L, summary.snapshotRead.medianMs)
        assertEquals(5.0 / 7.0, summary.fingerprintHitRate, 0.0001)
        assertEquals(1.0, summary.snapshotHitRate, 0.0001)
        assertEquals(1161, summary.medianNoteCount)
    }

    private fun sample(total: Long, snapshotRead: Long, fingerprintHit: Boolean) = ColdStartSample(
        processToActivityMs = 618,
        diBuildMs = 155,
        toGateStartMs = 270,
        gateResolveMs = 114,
        snapshotReadMs = snapshotRead,
        firstScanMs = 367,
        settleTailMs = 608,
        totalMs = total,
        noteCount = 1161,
        fingerprintHit = fingerprintHit,
        snapshotHit = true,
        hadMemoBase = true
    )
}
