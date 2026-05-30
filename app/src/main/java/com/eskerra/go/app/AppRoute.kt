package com.eskerra.go.app

import android.net.Uri
import com.eskerra.go.core.model.NoteId

/**
 * Single, centralized place that defines every navigation route in the app.
 * Note ids are encoded/decoded here at the route boundary so callers never deal
 * with URL escaping themselves.
 */
object AppRoute {
    const val INBOX = "inbox"
    const val ADD = "add"
    const val PODCASTS = "podcasts"
    const val DASHBOARD = "dashboard"
    const val MENU = "menu"

    const val NOTE_ARG = "noteId"
    const val NOTE_PATTERN = "note/{$NOTE_ARG}"

    /** Builds a concrete note route, encoding the id for safe use in a URL path. */
    fun note(id: NoteId): String = "note/${Uri.encode(id.value)}"

    /** Decodes the raw route argument back into a [NoteId]. */
    fun decodeNoteId(raw: String): NoteId = NoteId(Uri.decode(raw))
}
