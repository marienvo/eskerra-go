package com.eskerra.go.data.perf

import com.eskerra.go.core.model.ColdStartSample

/**
 * One sample as a single delimited row, matching the hand-rolled codec style used by the note
 * snapshot stores. Rows that do not parse are dropped by the caller rather than failing the
 * whole window, so a field-layout change costs at most one reporting period.
 */
internal object ColdStartSampleCodec {

    private const val FIELD_SEPARATOR = "|"
    private const val FIELD_COUNT = 12

    fun encode(sample: ColdStartSample): String = listOf(
        sample.processToActivityMs,
        sample.diBuildMs,
        sample.toGateStartMs,
        sample.gateResolveMs,
        sample.snapshotReadMs,
        sample.firstScanMs,
        sample.settleTailMs,
        sample.totalMs,
        sample.noteCount,
        if (sample.fingerprintHit) 1 else 0,
        if (sample.snapshotHit) 1 else 0,
        if (sample.hadMemoBase) 1 else 0
    ).joinToString(FIELD_SEPARATOR)

    fun decode(row: String): ColdStartSample? {
        val parts = row.split(FIELD_SEPARATOR)
        if (parts.size != FIELD_COUNT) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        return ColdStartSample(
            processToActivityMs = numbers[0],
            diBuildMs = numbers[1],
            toGateStartMs = numbers[2],
            gateResolveMs = numbers[3],
            snapshotReadMs = numbers[4],
            firstScanMs = numbers[5],
            settleTailMs = numbers[6],
            totalMs = numbers[7],
            noteCount = numbers[8].toInt(),
            fingerprintHit = numbers[9] == 1L,
            snapshotHit = numbers[10] == 1L,
            hadMemoBase = numbers[11] == 1L
        )
    }
}
