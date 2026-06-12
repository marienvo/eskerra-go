package com.eskerra.go.core.search

/**
 * Builds a safe FTS5 [MATCH] expression from user tokens (spec §12.4).
 * Each token is wrapped in double-quotes (phrase) and combined with implicit AND.
 */
object Fts5Query {
    private val operatorTokens = setOf("and", "or", "not", "near")

    fun buildSafeMatch(tokens: List<String>): String? {
        val parts = mutableListOf<String>()
        for (raw in tokens) {
            var token = raw.lowercase().trim()
            if (token.isEmpty()) continue
            token = token
                .replace("\"", " ")
                .replace("(", " ")
                .replace(")", " ")
                .replace("\\", " ")
            token = token.trim().replace(Regex("\\s+"), " ")
            token = token.trimStart('-')
            if (token.isEmpty()) continue
            val firstWord = token.split(Regex("\\s+")).firstOrNull().orEmpty()
            if (firstWord.lowercase() in operatorTokens) continue
            val escaped = token.replace("\"", "\"\"")
            parts += "\"$escaped\""
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun tokenizeQuery(queryTrimmed: String): List<String> =
        queryTrimmed.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
}
