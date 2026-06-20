package com.eskerra.go.data.notes

import com.eskerra.go.core.markdown.PreparedMarkdown
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ParsedMarkdownCacheTest {

    private class CountingPrepare {
        var callCount = 0
            private set

        val prepare: suspend (String) -> PreparedMarkdown = {
            callCount++
            PreparedMarkdown(emptyList())
        }
    }

    @Test
    fun get_miss_parsesAndCaches() = runTest {
        val counter = CountingPrepare()
        val cache = ParsedMarkdownCache(prepare = counter.prepare)

        cache.get("# A")
        cache.get("# A")

        assertEquals(1, counter.callCount)
    }

    @Test
    fun peek_isNullBeforeGet_andCachedAfter() = runTest {
        val counter = CountingPrepare()
        val cache = ParsedMarkdownCache(prepare = counter.prepare)

        assertNull(cache.peek("# A"))
        val parsed = cache.get("# A")

        assertSame(parsed, cache.peek("# A"))
        assertEquals(1, counter.callCount)
    }

    @Test
    fun warm_populatesCache_soGetDoesNotReparse() = runTest {
        val counter = CountingPrepare()
        val cache = ParsedMarkdownCache(prepare = counter.prepare)

        cache.warm("# A")
        cache.get("# A")

        assertEquals(1, counter.callCount)
    }

    @Test
    fun get_exceedsCapacity_evictsLruEntry() = runTest {
        val counter = CountingPrepare()
        val cache = ParsedMarkdownCache(maxSize = 2, prepare = counter.prepare)

        cache.get("A") // lru: [A]
        cache.get("B") // lru: [A(LRU), B]
        cache.get("C") // lru: [B, C]; A evicted

        val countAfterPopulate = counter.callCount // 3
        cache.get("B") // hit
        cache.get("A") // miss — A was evicted

        assertEquals(countAfterPopulate + 1, counter.callCount)
    }

    @Test
    fun peek_updatesRecency() = runTest {
        val counter = CountingPrepare()
        val cache = ParsedMarkdownCache(maxSize = 2, prepare = counter.prepare)

        cache.get("A") // lru: [A]
        cache.get("B") // lru: [A(LRU), B]
        cache.peek("A") // hit; A moves to MRU: lru: [B(LRU), A]
        cache.get("C") // miss; lru: [A, C]; B evicted

        val countAfterPopulate = counter.callCount // 3
        cache.get("A") // hit — A is still cached
        cache.get("B") // miss — B was the LRU entry evicted above

        assertEquals(countAfterPopulate + 1, counter.callCount)
    }
}
