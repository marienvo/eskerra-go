package com.eskerra.go.core.model

/** Result of a conditional (ETag) GET against the R2 `playlist.json`. */
sealed interface R2ConditionalResult {
    /** Server returned 304 — the cached etag is still current. */
    data object NotModified : R2ConditionalResult

    /** No object (404) or an empty body. */
    data object Missing : R2ConditionalResult

    /** A newer object, with its current [etag] (may be `null` if absent). */
    data class Updated(val entry: PlaylistEntry, val etag: String?) : R2ConditionalResult
}
