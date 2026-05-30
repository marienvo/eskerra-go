package com.eskerra.go.app

import com.eskerra.go.core.model.NoteId

/**
 * Single, centralized place that defines every navigation route in the app.
 * Note ids are encoded/decoded here at the route boundary so callers never deal
 * with URL escaping themselves.
 */
object AppRoute {
    const val INBOX = "inbox"
    const val CREATE_INBOX = "create-inbox"
    const val PODCASTS = "podcasts"
    const val DASHBOARD = "dashboard"
    const val MENU = "menu"

    const val NOTE_ARG = "noteId"
    const val NOTE_PATTERN = "note/{$NOTE_ARG}"

    const val EDITOR_ARG = "noteId"
    const val EDITOR_PATTERN = "editor/{$EDITOR_ARG}"

    /** Builds a concrete note route, encoding the id for safe use in a URL path. */
    fun note(id: NoteId): String = "note/${NoteRouteCodec.encode(id.value)}"

    /** Builds a concrete editor route, encoding the id for safe use in a URL path. */
    fun editor(id: NoteId): String = "editor/${NoteRouteCodec.encode(id.value)}"

    /** Decodes the raw route argument back into a [NoteId]. */
    fun decodeNoteId(raw: String): NoteId = NoteId(NoteRouteCodec.decode(raw))

    /** Decodes the raw editor route argument back into a [NoteId]. */
    fun decodeEditorNoteId(raw: String): NoteId = NoteId(NoteRouteCodec.decode(raw))
}
