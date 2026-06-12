package com.eskerra.go.core.search

import kotlin.math.abs

private const val SNIPPET_MAX_CHARS = 160

object SearchRanker {
    fun rank(candidate: SearchCandidate, fullQuery: String, tokens: List<String>): RankedNote {
        val qLower = fullQuery.lowercase()
        val titleL = candidate.title.lowercase()
        val fileL = candidate.filename.lowercase()
        val relL = candidate.relPath.lowercase()
        val bodyL = candidate.body.lowercase()
        var tier = 0f
        var best = VaultSearchBestField.BODY
        if (titleL.contains(qLower) || relL.contains(qLower)) {
            tier = 40_000f
            best =
                if (titleL.contains(
                        qLower
                    )
                ) {
                    VaultSearchBestField.TITLE
                } else {
                    VaultSearchBestField.PATH
                }
        } else if (hasPrefixHit(titleL, fileL, relL, tokens)) {
            tier = 25_000f
            best = VaultSearchBestField.PATH
        } else if (fullQuery.length >= 4 && fuzzyTitlePathHit(titleL, fileL, relL, tokens)) {
            tier = 12_000f
            best = VaultSearchBestField.PATH
        }
        val snippet = firstBodySnippetLine(candidate.body, qLower, tokens)
        val matchCount = countTokenMatches(tokens, titleL, fileL, relL, bodyL)
        return RankedNote(
            uri = candidate.uri,
            relPath = candidate.relPath,
            title = candidate.title,
            bestField = best,
            matchCount = matchCount,
            score = tier + candidate.bm25 * 0.02f,
            snippetText = snippet?.second,
            snippetLine = snippet?.first
        )
    }

    private fun countTokenMatches(
        tokens: List<String>,
        titleL: String,
        fileL: String,
        relL: String,
        bodyL: String
    ): Int {
        var count = 0
        for (token in tokens) {
            val lowered = token.lowercase().trim()
            if (lowered.length < 2) continue
            if (titleL.contains(lowered) ||
                fileL.contains(lowered) ||
                relL.contains(lowered) ||
                bodyL.contains(lowered)
            ) {
                count++
            }
        }
        return if (count > 0) count else 1
    }

    private fun hasPrefixHit(
        titleL: String,
        fileL: String,
        relL: String,
        tokens: List<String>
    ): Boolean {
        for (token in tokens) {
            if (token.length < 3) continue
            val lowered = token.lowercase()
            if (titleL.split(Regex("\\s+")).any { it.startsWith(lowered) }) return true
            if (fileL.split(Regex("\\s+|[/\\\\]")).any { it.startsWith(lowered) }) return true
            if (relL.split(Regex("\\s+|[/\\\\]")).any { it.startsWith(lowered) }) return true
        }
        return false
    }

    private fun fuzzyTitlePathHit(
        titleL: String,
        fileL: String,
        relL: String,
        tokens: List<String>
    ): Boolean {
        val haystack = "$titleL $fileL $relL"
        for (token in tokens) {
            if (token.length < 4) continue
            val lowered = token.lowercase()
            val maxDistance = maxEditDistanceForQuery(lowered.length)
            for (word in haystack.split(Regex("\\s+|[/\\\\._-]+"))) {
                if (word.isEmpty()) continue
                val trimmed = word.trim().lowercase()
                if (abs(trimmed.length - lowered.length) > maxDistance) continue
                if (boundedLevenshtein(trimmed, lowered, maxDistance) != null) return true
            }
        }
        return false
    }

    private fun maxEditDistanceForQuery(length: Int): Int = when {
        length <= 2 -> 0
        length <= 5 -> 1
        else -> 2
    }

    private fun boundedLevenshtein(a: String, b: String, maxDist: Int): Int? {
        val n = a.length
        val m = b.length
        if (abs(n - m) > maxDist) return null
        var previous = IntArray(m + 1) { it }
        var current = IntArray(m + 1)
        for (i in 1..n) {
            current[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        val distance = previous[m]
        return if (distance <= maxDist) distance else null
    }

    private fun firstBodySnippetLine(
        body: String,
        fullLower: String,
        tokens: List<String>
    ): Pair<Int, String>? {
        var lineNumber = 0
        for (line in body.lineSequence()) {
            lineNumber++
            val lowered = line.lowercase()
            if (fullLower.isNotEmpty() && lowered.contains(fullLower)) {
                return lineNumber to line.trim().take(SNIPPET_MAX_CHARS)
            }
            for (token in tokens) {
                if (token.length >= 3 && lowered.contains(token.lowercase())) {
                    return lineNumber to line.trim().take(SNIPPET_MAX_CHARS)
                }
            }
        }
        return null
    }
}
