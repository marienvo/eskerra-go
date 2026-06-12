package com.eskerra.go.core.search

/** Which indexed field drove the tier boost for a search hit (spec §12.5). */
enum class VaultSearchBestField {
    TITLE,
    PATH,
    BODY
}

data class VaultSearchNoteSnippet(val lineNumber: Int?, val text: String)

data class VaultSearchNoteResult(
    val uri: String,
    val relativePath: String,
    val title: String,
    val bestField: VaultSearchBestField,
    val matchCount: Int,
    val score: Float,
    val snippets: List<VaultSearchNoteSnippet>
)

data class SearchCandidate(
    val uri: String,
    val relPath: String,
    val title: String,
    val filename: String,
    val body: String,
    val bm25: Float
)

data class RankedNote(
    val uri: String,
    val relPath: String,
    val title: String,
    val bestField: VaultSearchBestField,
    val matchCount: Int,
    val score: Float,
    val snippetText: String?,
    val snippetLine: Int?
)

data class VaultSearchIndexStatus(
    val vaultInstanceId: String,
    val indexReady: Boolean,
    val bodiesIndexReady: Boolean,
    val indexedNotes: Int = 0
)

data class VaultSearchResult(
    val searchId: Long,
    val vaultInstanceId: String,
    val notes: List<VaultSearchNoteResult>,
    val status: VaultSearchIndexStatus
)

data class FileSnapshot(val size: Long, val lastModified: Long)

data class ReconcileDiffResult(
    val added: List<String>,
    val updated: List<String>,
    val removed: List<String>
)

data class VaultSearchHighlightSegment(val text: String, val highlighted: Boolean)
