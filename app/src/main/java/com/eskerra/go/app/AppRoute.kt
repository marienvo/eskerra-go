package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId

/**
 * Single, centralized place that defines every navigation route in the app.
 * Note ids are encoded/decoded here at the route boundary so callers never deal
 * with URL escaping themselves.
 */
object AppRoute {
    const val HOME_GRAPH = "home-graph"
    const val PODCASTS_GRAPH = "podcasts-graph"

    const val INBOX = "inbox"
    const val SEARCH = "search"
    const val SEARCH_QUERY_ARG = "q"

    /**
     * Search destination with an optional pre-filled query. The `q` argument defaults to empty, so
     * navigating to the bare [SEARCH] string still matches this pattern (menu/inbox entry points).
     */
    const val SEARCH_PATTERN = "search?$SEARCH_QUERY_ARG={$SEARCH_QUERY_ARG}"
    const val PODCASTS = "podcasts"
    const val SYNC = "sync"
    const val SYNC_SETTINGS = "sync-settings"

    const val NOTE_ARG = "noteId"
    const val NOTE_PATTERN = "note/{$NOTE_ARG}"

    const val EDITOR_ARG = "noteId"
    const val EDITOR_PATTERN = "editor/{$EDITOR_ARG}"

    /** Builds a concrete note route, encoding the id for safe use in a URL path. */
    fun note(id: NoteId): String = "note/${NoteRouteCodec.encode(id.value)}"

    /** Builds a concrete editor route, encoding the id for safe use in a URL path. */
    fun editor(id: NoteId): String = "editor/${NoteRouteCodec.encode(id.value)}"

    /** Builds a search route pre-filled with [query], encoding it for safe use in the URL. */
    fun search(query: String): String = "search?$SEARCH_QUERY_ARG=${NoteRouteCodec.encode(query)}"

    /** Decodes the raw search query route argument back into plain text. */
    fun decodeSearchQuery(raw: String): String = NoteRouteCodec.decode(raw)

    /** Decodes the raw route argument back into a [NoteId]. */
    fun decodeNoteId(raw: String): NoteId = NoteId(NoteRouteCodec.decode(raw))

    /** Decodes the raw editor route argument back into a [NoteId]. */
    fun decodeEditorNoteId(raw: String): NoteId = NoteId(NoteRouteCodec.decode(raw))

    /** Whether [route] is the vault search destination (graph pattern or concrete query route). */
    internal fun isSearchRoute(route: String?): Boolean {
        if (route == null) {
            return false
        }
        if (route == SEARCH || route == SEARCH_PATTERN) {
            return true
        }
        return route.startsWith("$SEARCH?$SEARCH_QUERY_ARG=")
    }

    /** Whether [route] is a concrete note-reader destination (not the nav graph pattern). */
    internal fun isConcreteNoteRoute(route: String?): Boolean {
        if (route == null || route == NOTE_PATTERN) return false
        val prefix = NOTE_PATTERN.substringBefore("{")
        return route.startsWith(prefix)
    }
}
