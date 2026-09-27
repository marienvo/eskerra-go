package com.eskerra.go.core.repository

import com.eskerra.go.core.markdown.PreparedMarkdown

/** Cache abstraction for pre-parsed vault markdown bodies; implemented by `ParsedMarkdownCache`. */
interface ParsedMarkdownCachePort {
    fun peek(markdown: String): PreparedMarkdown?

    /** Returns the prepared body, parsing it (off the main thread) on a cache miss. */
    suspend fun get(markdown: String): PreparedMarkdown

    suspend fun warm(markdown: String)
}
