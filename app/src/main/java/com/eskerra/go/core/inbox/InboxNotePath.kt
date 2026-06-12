package com.eskerra.go.core.inbox

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/** Inbox path guards and delete resolution. Mirrors mobile `isNoteUriInInbox` semantics for paths. */
object InboxNotePath {

    const val INBOX_DIRECTORY = "Inbox"

    private const val INBOX_PREFIX = "$INBOX_DIRECTORY/"

    fun isInboxRelativePath(relativePath: String): Boolean =
        relativePath.startsWith(INBOX_PREFIX) &&
            relativePath.indexOf('/', startIndex = INBOX_PREFIX.length) < 0

    fun resolveCanonicalDeleteNote(
        inputId: NoteId,
        availableNotes: List<NoteSummary>
    ): NoteSummary? {
        val exactMatch = availableNotes.find { it.id == inputId }
        if (exactMatch != null) {
            return exactMatch
        }

        val inputFileName = inputId.value.substringAfterLast('/')
        val sameNameMatches = availableNotes.filter { it.fileName == inputFileName }
        return sameNameMatches.singleOrNull()
    }
}
