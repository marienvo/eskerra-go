package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMergeTest {

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
    fun `normalize fills optional defaults`() {
        val json = Json.parseToJsonElement(
            """{"episodeId":"e1","mp3Url":"u","positionMs":10,"durationMs":null}"""
        )
        val entry = normalizePlaylistEntryForSync(json)
        assertEquals(PlaylistEntry("e1", "u", 10, null, 0, "", 0), entry)
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
}
