package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMergeTest {

    // ── normalize ─────────────────────────────────────────────────────────────

    @Test
    fun `normalize fills optional defaults`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e1","mp3Url":"u","positionMs":10,"durationMs":null}"""
        )
        val entry = normalizePlaylistEntryForSync(json)
        assertEquals(PlaylistEntry("e1", "u", 10, null, 0, "", 0), entry)
    }

    @Test
    fun `normalize accepts legacy JSON without updatedAt`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e","mp3Url":"https://x/y.mp3","positionMs":1,"durationMs":1000}"""
        )
        assertEquals(
            PlaylistEntry("e", "https://x/y.mp3", 1, 1000, 0, "", 0),
            normalizePlaylistEntryForSync(json)
        )
    }

    @Test
    fun `normalize rejects invalid updatedAt when present`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e","mp3Url":"u","positionMs":1,"durationMs":null,"updatedAt":"x"}"""
        )
        assertNull(normalizePlaylistEntryForSync(json))
    }

    @Test
    fun `normalize rejects invalid playbackOwnerId when present`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e","mp3Url":"u","positionMs":1,"durationMs":null,"playbackOwnerId":42}"""
        )
        assertNull(normalizePlaylistEntryForSync(json))
    }

    @Test
    fun `normalize rejects invalid controlRevision when present`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e","mp3Url":"u","positionMs":1,"durationMs":null,"controlRevision":"bad"}"""
        )
        assertNull(normalizePlaylistEntryForSync(json))
    }

    @Test
    fun `normalize rejects missing required fields`() {
        val json = Json.parseToJsonElement("""{"mp3Url":"u","positionMs":10,"durationMs":null}""")
        assertNull(normalizePlaylistEntryForSync(json))
    }

    @Test
    fun `normalize rejects non-object`() {
        assertNull(normalizePlaylistEntryForSync(Json.parseToJsonElement("[]")))
    }

    // ── serialize ─────────────────────────────────────────────────────────────

    @Test
    fun `serialize emits stable key order with trailing newline`() {
        val entry = PlaylistEntry(
            episodeId = "e1",
            mp3Url = "http://x/a.mp3",
            positionMs = 1000,
            durationMs = 5000,
            updatedAt = 42,
            playbackOwnerId = "dev",
            controlRevision = 3
        )
        val expected = """
            {
              "episodeId": "e1",
              "mp3Url": "http://x/a.mp3",
              "positionMs": 1000,
              "durationMs": 5000,
              "updatedAt": 42,
              "playbackOwnerId": "dev",
              "controlRevision": 3
            }

        """.trimIndent()
        assertEquals(expected, serializePlaylistEntry(entry))
    }

    @Test
    fun `serialize writes null duration`() {
        val entry = PlaylistEntry("e1", "u", 0, null)
        assertTrue(serializePlaylistEntry(entry).contains("\"durationMs\": null"))
    }

    @Test
    fun `serialize includes updatedAt`() {
        val entry = PlaylistEntry(
            "e",
            "https://x/y.mp3",
            1,
            1000,
            updatedAt = 99,
            playbackOwnerId = "",
            controlRevision = 0
        )
        val parsed = Json.parseToJsonElement(serializePlaylistEntry(entry))
        assertEquals(entry, normalizePlaylistEntryForSync(parsed))
    }

    // ── pickNewerPlaylistEntry ────────────────────────────────────────────────

    private val base = PlaylistEntry(
        episodeId = "e",
        mp3Url = "https://x/y.mp3",
        positionMs = 1,
        durationMs = 1000
    )

    @Test
    fun `pickNewerPlaylistEntry prefers higher controlRevision`() {
        val a = base.copy(controlRevision = 1, updatedAt = 10)
        val b = base.copy(controlRevision = 2, updatedAt = 5)
        assertEquals(b, pickNewerPlaylistEntry(a, b))
        assertEquals(b, pickNewerPlaylistEntry(b, a))
    }

    @Test
    fun `pickNewerPlaylistEntry prefers higher updatedAt when controlRevision ties`() {
        val a = base.copy(controlRevision = 1, updatedAt = 10)
        val b = base.copy(controlRevision = 1, updatedAt = 20)
        assertEquals(b, pickNewerPlaylistEntry(a, b))
        assertEquals(b, pickNewerPlaylistEntry(b, a))
    }

    @Test
    fun `pickNewerPlaylistEntry tie prefers second`() {
        val a = base.copy(controlRevision = 1, updatedAt = 5)
        val b = base.copy(episodeId = "remote", controlRevision = 1, updatedAt = 5)
        assertEquals(b, pickNewerPlaylistEntry(a, b))
    }

    @Test
    fun `pickNewerPlaylistEntry returns second when first is null`() {
        assertEquals(base, pickNewerPlaylistEntry(null, base))
    }

    @Test
    fun `pickNewerPlaylistEntry returns first when second is null`() {
        assertEquals(base, pickNewerPlaylistEntry(base, null))
    }

    @Test
    fun `pickNewerPlaylistEntry returns null when both null`() {
        assertNull(pickNewerPlaylistEntry(null, null))
    }

    // ── isPlaylistR2PollEchoFromOwnDevice ─────────────────────────────────────

    @Test
    fun `isPlaylistR2PollEchoFromOwnDevice true only when owner matches non-empty device id`() {
        val entry = base.copy(controlRevision = 1, playbackOwnerId = "device-a", updatedAt = 1)
        assertTrue(isPlaylistR2PollEchoFromOwnDevice(entry, "device-a"))
        assertFalse(isPlaylistR2PollEchoFromOwnDevice(entry, "device-b"))
        assertFalse(isPlaylistR2PollEchoFromOwnDevice(entry.copy(playbackOwnerId = ""), "device-a"))
        assertFalse(isPlaylistR2PollEchoFromOwnDevice(entry, ""))
        assertTrue(isPlaylistR2PollEchoFromOwnDevice(entry, "  device-a  "))
    }

    // ── isRemotePlaylistNewerThanKnown ────────────────────────────────────────

    @Test
    fun `isRemotePlaylistNewerThanKnown true when controlRevision higher`() {
        val remote = base.copy(controlRevision = 2, updatedAt = 5)
        assertTrue(
            isRemotePlaylistNewerThanKnown(remote, knownUpdatedAtMs = 100, knownControlRevision = 1)
        )
    }

    @Test
    fun `isRemotePlaylistNewerThanKnown false when controlRevision lower`() {
        val remote = base.copy(controlRevision = 1, updatedAt = 100)
        assertFalse(
            isRemotePlaylistNewerThanKnown(remote, knownUpdatedAtMs = 5, knownControlRevision = 2)
        )
    }

    @Test
    fun `isRemotePlaylistNewerThanKnown uses updatedAt as tiebreaker`() {
        val older = base.copy(controlRevision = 1, updatedAt = 10)
        val newer = base.copy(controlRevision = 1, updatedAt = 20)
        assertFalse(
            isRemotePlaylistNewerThanKnown(older, knownUpdatedAtMs = 15, knownControlRevision = 1)
        )
        assertTrue(
            isRemotePlaylistNewerThanKnown(newer, knownUpdatedAtMs = 15, knownControlRevision = 1)
        )
    }

    // ── buildPlaylistEntryForWrite ────────────────────────────────────────────

    @Test
    fun `buildPlaylistEntryForWrite bumps controlRevision and stamps owner`() {
        val b = base.copy(controlRevision = 2, updatedAt = 100)
        val result = buildPlaylistEntryForWrite(b, "dev-1", nowMs = 50)
        assertEquals(3, result.controlRevision)
        assertEquals("dev-1", result.playbackOwnerId)
        assertEquals(100L, result.updatedAt) // max(50, 100)
    }

    @Test
    fun `buildPlaylistEntryForWrite advances updatedAt when nowMs is greater`() {
        val b = base.copy(controlRevision = 0, updatedAt = 50)
        val result = buildPlaylistEntryForWrite(b, "dev-1", nowMs = 200)
        assertEquals(200L, result.updatedAt)
    }

    @Test
    fun `buildPlaylistEntryForWrite applies patch fields`() {
        val b = base.copy(positionMs = 0, episodeId = "old")
        val result = buildPlaylistEntryForWrite(
            b,
            "dev-1",
            nowMs = 1,
            positionMs = 999,
            episodeId = "new",
            mp3Url = "https://new/ep.mp3"
        )
        assertEquals(999L, result.positionMs)
        assertEquals("new", result.episodeId)
        assertEquals("https://new/ep.mp3", result.mp3Url)
    }
}
