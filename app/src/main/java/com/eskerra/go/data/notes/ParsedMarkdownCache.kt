package com.eskerra.go.data.notes

import com.eskerra.go.core.markdown.PreparedMarkdown
import com.eskerra.go.core.markdown.prepareVaultMarkdown

/**
 * Bounded LRU cache of [PreparedMarkdown] keyed by the raw note body, so repeat renders (and
 * prefetched links) paint atomically with no re-parse. Misses delegate to [prepare] — the heavy,
 * off-main-thread parse — and store the result.
 *
 * Parsing is content-derived (registry/theme/now are applied at render time, not baked in), so the
 * body text is a complete cache key. Thread-safe: synchronous [peek]/[get] guard the LRU under the
 * instance monitor while the (suspending) [prepare] call runs outside the lock.
 */
class ParsedMarkdownCache(
    private val maxSize: Int = DEFAULT_SIZE,
    private val prepare: suspend (String) -> PreparedMarkdown = { prepareVaultMarkdown(it) }
) {

    private val lru = object : LinkedHashMap<String, PreparedMarkdown>(maxSize * 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, PreparedMarkdown>): Boolean =
            size > maxSize
    }

    /** Synchronous warm-cache lookup for atomic first-frame rendering; `null` on a miss. */
    @Synchronized
    fun peek(markdown: String): PreparedMarkdown? = lru[markdown]

    /** Returns the prepared body, parsing it (off the main thread) on a cache miss. */
    suspend fun get(markdown: String): PreparedMarkdown {
        peek(markdown)?.let { return it }
        val prepared = prepare(markdown)
        store(markdown, prepared)
        return prepared
    }

    /** Pre-parses [markdown] into the cache without rendering it (link/note prefetch). */
    suspend fun warm(markdown: String) {
        get(markdown)
    }

    @Synchronized
    private fun store(markdown: String, prepared: PreparedMarkdown) {
        lru[markdown] = prepared
    }

    companion object {
        const val DEFAULT_SIZE = 16
    }
}
