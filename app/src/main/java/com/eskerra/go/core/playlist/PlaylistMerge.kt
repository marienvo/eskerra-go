package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long

/**
 * Pure playlist parse/serialize, mirroring the relevant parts of
 * `packages/eskerra-core/src/playlist.ts`. The conflict-resolution merge
 * functions (`pickNewerPlaylistEntry`, `isRemotePlaylistNewerThanKnown`,
 * `buildPlaylistEntryForWrite`, `isPlaylistR2PollEchoFromOwnDevice`) land here
 * in Phase 6; this file currently carries the codec the R2 client needs.
 */
@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Normalize arbitrary JSON into a [PlaylistEntry], or `null` on invalid core shape.
 *
 * Required: `episodeId: string`, `mp3Url: string`, `positionMs: number`,
 * `durationMs: number | null`. Optional/legacy fields default when missing or
 * the wrong type: `updatedAt` → `0`, `playbackOwnerId` → `""`, `controlRevision` → `0`.
 */
fun normalizePlaylistEntryForSync(element: JsonElement?): PlaylistEntry? {
    val obj = element as? JsonObject ?: return null

    val episodeId = obj.stringOrNull("episodeId") ?: return null
    val mp3Url = obj.stringOrNull("mp3Url") ?: return null
    val positionMs = obj.finiteOrNull("positionMs") ?: return null
    val durationMs = obj.durationOrInvalid("durationMs") ?: return null

    return PlaylistEntry(
        episodeId = episodeId,
        mp3Url = mp3Url,
        positionMs = positionMs,
        durationMs = durationMs.value,
        updatedAt = obj.finiteOrNull("updatedAt") ?: 0L,
        playbackOwnerId = obj.stringOrNull("playbackOwnerId") ?: "",
        controlRevision = obj.finiteOrNull("controlRevision") ?: 0L
    )
}

/** `JSON.stringify(entry, null, 2)` + trailing newline, with stable key order. */
fun serializePlaylistEntry(entry: PlaylistEntry): String {
    val map = linkedMapOf<String, JsonElement>(
        "episodeId" to JsonPrimitive(entry.episodeId),
        "mp3Url" to JsonPrimitive(entry.mp3Url),
        "positionMs" to JsonPrimitive(entry.positionMs),
        "durationMs" to (entry.durationMs?.let { JsonPrimitive(it) } ?: JsonNull),
        "updatedAt" to JsonPrimitive(entry.updatedAt),
        "playbackOwnerId" to JsonPrimitive(entry.playbackOwnerId),
        "controlRevision" to JsonPrimitive(entry.controlRevision)
    )
    return prettyJson.encodeToString(JsonObject.serializer(), JsonObject(map)) + "\n"
}

private fun JsonObject.stringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

/** Finite (non-NaN, non-infinite) number → Long, else null. */
private fun JsonObject.finiteOrNull(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    val asDouble = primitive.doubleOrNull ?: return null
    if (asDouble.isNaN() || asDouble.isInfinite()) return null
    return primitive.runCatching { long }.getOrNull() ?: asDouble.toLong()
}

/** Wrapper so a valid `null` duration is distinguishable from an invalid value. */
private class DurationValue(val value: Long?)

/** `null`/absent → valid null; finite number → that number; anything else → invalid (null wrapper). */
private fun JsonObject.durationOrInvalid(key: String): DurationValue? {
    val element = this[key]
    if (element == null || element is JsonNull) return DurationValue(null)
    val finite = finiteOrNull(key) ?: return null
    return DurationValue(finite)
}
