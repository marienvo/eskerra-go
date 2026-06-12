package com.eskerra.go.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId

/**
 * Cross-screen navigation signals for the note reader/inbox (saved-state handle flags) and external
 * link opening. Extracted from [App] to keep that file within its module budget.
 */

internal const val NOTES_CHANGED_KEY = "notesChanged"
internal const val NOTE_CONTENT_CHANGED_KEY = "noteContentChanged"

internal fun consumeNoteReaderChanged(
    currentRoute: String?,
    noteId: NoteId,
    savedStateHandle: SavedStateHandle
): Boolean {
    if (currentRoute != AppRoute.NOTE_PATTERN) return false
    return savedStateHandle.remove<Boolean>(NOTE_CONTENT_CHANGED_KEY) == true
}

internal fun NavHostController.markInboxNotesChanged() {
    runCatching {
        getBackStackEntry(AppRoute.INBOX).savedStateHandle[NOTES_CHANGED_KEY] = true
    }
}

internal fun NavHostController.markNoteReaderChanged(noteId: NoteId) {
    runCatching {
        getBackStackEntry(AppRoute.note(noteId))
            .savedStateHandle[NOTE_CONTENT_CHANGED_KEY] = true
    }
}

/** Opens an external markdown link (spec §8.2) in the system browser; failures are swallowed. */
internal fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
