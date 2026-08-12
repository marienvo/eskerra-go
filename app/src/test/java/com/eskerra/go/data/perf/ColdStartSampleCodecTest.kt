package com.eskerra.go.data.perf

import com.eskerra.go.core.model.ColdStartSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColdStartSampleCodecTest {

    @Test
    fun roundTripsEveryField() {
        val sample = ColdStartSample(
            processToActivityMs = 618,
            diBuildMs = 155,
            toGateStartMs = 270,
            gateResolveMs = 114,
            snapshotReadMs = 743,
            firstScanMs = 2100,
            settleTailMs = 608,
            totalMs = 4962,
            noteCount = 1161,
            fingerprintHit = false,
            snapshotHit = true,
            hadMemoBase = false
        )

        assertEquals(sample, ColdStartSampleCodec.decode(ColdStartSampleCodec.encode(sample)))
    }

    @Test
    fun rejectsRowsThatDoNotParse() {
        assertNull(ColdStartSampleCodec.decode(""))
        assertNull(ColdStartSampleCodec.decode("1|2|3"))
        assertNull(ColdStartSampleCodec.decode((1..12).joinToString("|") { "x" }))
    }

    /** A row from an older field layout is dropped, not crashed on. */
    @Test
    fun rejectsRowsWithADifferentFieldCount() {
        assertNull(ColdStartSampleCodec.decode((1..13).joinToString("|")))
        assertNull(ColdStartSampleCodec.decode((1..11).joinToString("|")))
    }
}
