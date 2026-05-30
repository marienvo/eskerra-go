package com.eskerra.go.core.model

/** Reader presentation for a loaded note, including pre-resolved wiki-link segments. */
data class NoteReaderDocument(
    val note: NoteSummary,
    val content: NoteContent,
    val segments: List<NoteReaderSegment>
)

sealed interface NoteReaderSegment {
    data class Text(val text: String) : NoteReaderSegment

    data class ResolvedLink(val label: String, val target: NoteId) : NoteReaderSegment

    data class MissingLink(val label: String, val reason: MissingWikiLinkReason) : NoteReaderSegment

    data class AmbiguousLink(val label: String, val candidateCount: Int) : NoteReaderSegment
}
