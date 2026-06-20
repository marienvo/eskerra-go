package com.eskerra.go.core.repository

import com.eskerra.go.core.markdown.PreparedMarkdown

/** Cache abstraction for pre-parsed vault markdown bodies; implemented by `ParsedMarkdownCache`. */
interface ParsedMarkdownCachePort {
    fun peek(markdown: String): PreparedMarkdown?

    suspend fun warm(markdown: String)
}
