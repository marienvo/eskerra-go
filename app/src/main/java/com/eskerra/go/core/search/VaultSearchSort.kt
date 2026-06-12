package com.eskerra.go.core.search

/** Lower rank sorts earlier: title, then path, then body (tie-breaker after score). */
fun vaultSearchBestFieldRank(field: VaultSearchBestField): Int = when (field) {
    VaultSearchBestField.TITLE -> 0
    VaultSearchBestField.PATH -> 1
    VaultSearchBestField.BODY -> 2
}

/** Sort note-level results: higher score first, then stronger bestField, then uri. */
fun compareVaultSearchNotes(a: VaultSearchNoteResult, b: VaultSearchNoteResult): Int {
    if (a.score != b.score) {
        return b.score.compareTo(a.score)
    }
    val fieldA = vaultSearchBestFieldRank(a.bestField)
    val fieldB = vaultSearchBestFieldRank(b.bestField)
    if (fieldA != fieldB) {
        return fieldA.compareTo(fieldB)
    }
    return a.uri.compareTo(b.uri)
}

fun rankedNoteToResult(ranked: RankedNote): VaultSearchNoteResult {
    val snippets = if (ranked.snippetText != null) {
        listOf(VaultSearchNoteSnippet(lineNumber = ranked.snippetLine, text = ranked.snippetText))
    } else {
        emptyList()
    }
    return VaultSearchNoteResult(
        uri = ranked.uri,
        relativePath = ranked.relPath,
        title = ranked.title,
        bestField = ranked.bestField,
        matchCount = ranked.matchCount,
        score = ranked.score,
        snippets = snippets
    )
}
