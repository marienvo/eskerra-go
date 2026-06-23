package com.eskerra.go.core.inbox

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/** Inbox path guards and delete resolution. Mirrors mobile `isNoteUriInInbox` semantics for paths. */
object InboxNotePath {

    const val INBOX_DIRECTORY = "Inbox"

    /** Returns the inbox prefix for [hubFolder]: `"Inbox/"` at the vault root or `"<hub>/Inbox/"` inside a hub. */
    fun inboxPrefixFor(hubFolder: String): String =
        if (hubFolder.isEmpty()) "$INBOX_DIRECTORY/" else "$hubFolder/$INBOX_DIRECTORY/"

    /**
     * True when [relativePath] is a direct inbox child at vault root (`Inbox/<file>.md`) or
     * inside a hub folder (`<hub>/Inbox/<file>.md`). Sub-sub-folders are not accepted.
     */
    fun isInboxRelativePath(relativePath: String): Boolean {
        val parts = relativePath.split('/')
        return when (parts.size) {
            2 -> parts[0] == INBOX_DIRECTORY
            3 -> parts[1] == INBOX_DIRECTORY
            else -> false
        }
    }

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
