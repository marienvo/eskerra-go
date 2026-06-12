package com.eskerra.go.core.search

/** Matches desktop `FUZZY_MIN_QUERY_CHARS` / notebox `VAULT_SEARCH_HIGHLIGHT_MIN_TOKEN_CHARS`. */
const val VAULT_SEARCH_HIGHLIGHT_MIN_TOKEN_CHARS = 3

fun vaultSearchHighlightNeedles(queryTrimmed: String): List<String> {
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    fun add(raw: String) {
        val lower = raw.lowercase()
        if (lower.isEmpty() || lower in seen) return
        seen += lower
        out += lower
    }
    if (queryTrimmed.isNotEmpty()) {
        add(queryTrimmed)
    }
    for (token in queryTrimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        if (token.length >= VAULT_SEARCH_HIGHLIGHT_MIN_TOKEN_CHARS) {
            add(token)
        }
    }
    return out
}

fun vaultSearchHighlightSegments(
    text: String,
    queryTrimmed: String
): List<VaultSearchHighlightSegment> {
    if (text.isEmpty()) return emptyList()
    val needles = vaultSearchHighlightNeedles(queryTrimmed)
    if (needles.isEmpty()) {
        return listOf(VaultSearchHighlightSegment(text = text, highlighted = false))
    }
    val lower = text.lowercase()
    val ranges = mergeIntervals(collectMatchRanges(lower, needles))
    if (ranges.isEmpty()) {
        return listOf(VaultSearchHighlightSegment(text = text, highlighted = false))
    }
    val segments = mutableListOf<VaultSearchHighlightSegment>()
    var cursor = 0
    for ((start, end) in ranges) {
        if (cursor < start) {
            segments +=
                VaultSearchHighlightSegment(
                    text = text.substring(cursor, start),
                    highlighted = false
                )
        }
        segments +=
            VaultSearchHighlightSegment(text = text.substring(start, end), highlighted = true)
        cursor = end
    }
    if (cursor < text.length) {
        segments += VaultSearchHighlightSegment(text = text.substring(cursor), highlighted = false)
    }
    return segments
}

private fun collectMatchRanges(
    haystackLower: String,
    needlesLower: List<String>
): List<Pair<Int, Int>> {
    val ranges = mutableListOf<Pair<Int, Int>>()
    for (needle in needlesLower) {
        if (needle.isEmpty()) continue
        var from = 0
        while (from <= haystackLower.length - needle.length) {
            val index = haystackLower.indexOf(needle, from)
            if (index == -1) break
            ranges += index to (index + needle.length)
            from = index + 1
        }
    }
    return ranges
}

private fun mergeIntervals(intervals: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    if (intervals.isEmpty()) return emptyList()
    val sorted = intervals.sortedBy { it.first }
    val out = mutableListOf<Pair<Int, Int>>()
    var (currentStart, currentEnd) = sorted.first()
    for (index in 1 until sorted.size) {
        val (start, end) = sorted[index]
        if (start <= currentEnd) {
            currentEnd = maxOf(currentEnd, end)
        } else {
            out += currentStart to currentEnd
            currentStart = start
            currentEnd = end
        }
    }
    out += currentStart to currentEnd
    return out
}
