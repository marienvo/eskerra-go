package com.eskerra.go.data.todayhub

import com.eskerra.go.core.model.GateFingerprint
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.todayhub.TodayHubFrontmatter
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.core.todayhub.TodayHubRow
import com.eskerra.go.core.todayhub.TodayHubSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayHubSnapshotCodecTest {

    private val snapshot = TodayHubSnapshot(
        hubs = listOf(
            TodayHubRef(NoteId("Daily/Today.md"), "Daily"),
            TodayHubRef(NoteId("Work/Today.md"), "Work")
        ),
        activeHubId = NoteId("Daily/Today.md"),
        settings = TodayHubFrontmatter.Settings(
            columns = listOf("Tasks", "Notes"),
            start = TodayHubFrontmatter.StartDay.MONDAY
        ),
        introMarkdown = "Intro \"quoted\"\nbody",
        availableWeekStems = listOf("2026-03-30", "2026-04-06"),
        selectedWeekStem = "2026-04-06",
        row = TodayHubRow(
            rowNoteId = NoteId("Daily/2026-04-06.md"),
            weekStartStem = "2026-04-06",
            columns = listOf("default", "tasks},{not-split", "notes")
        )
    )

    @Test
    fun encodeAndDecode_roundTripsSnapshot() {
        val fingerprint = GateFingerprint("abc123")
        val raw = TodayHubSnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            snapshot = snapshot
        )

        assertEquals(snapshot, TodayHubSnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun encodeAndDecode_roundTripsNullRow() {
        val fingerprint = GateFingerprint("abc123")
        val raw = TodayHubSnapshotCodec.encode(
            fingerprint = fingerprint,
            savedAtEpochMs = 99L,
            snapshot = snapshot.copy(row = null)
        )

        assertEquals(snapshot.copy(row = null), TodayHubSnapshotCodec.decode(raw, fingerprint))
    }

    @Test
    fun decode_rejectsFingerprintMismatch() {
        val raw = TodayHubSnapshotCodec.encode(
            fingerprint = GateFingerprint("abc123"),
            savedAtEpochMs = 99L,
            snapshot = snapshot
        )

        val result = runCatching {
            TodayHubSnapshotCodec.decode(raw, GateFingerprint("other"))
        }.exceptionOrNull()

        assertEquals("snapshot fingerprint mismatch", result?.message)
    }
}
