package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedUrlTest {

    @Test
    fun soleUrlOrNull_acceptsBareHttpsUrl() {
        assertEquals("https://example.com/a", SharedUrl.soleUrlOrNull("https://example.com/a"))
    }

    @Test
    fun soleUrlOrNull_trimsSurroundingWhitespace() {
        assertEquals("https://example.com", SharedUrl.soleUrlOrNull("  https://example.com \n"))
    }

    @Test
    fun soleUrlOrNull_acceptsQueryAndFragment() {
        val url = "https://example.com/a?b=c&d=e#frag"
        assertEquals(url, SharedUrl.soleUrlOrNull(url))
    }

    @Test
    fun soleUrlOrNull_acceptsUppercaseScheme() {
        assertEquals("HTTPS://Example.com", SharedUrl.soleUrlOrNull("HTTPS://Example.com"))
    }

    @Test
    fun soleUrlOrNull_acceptsLocalhostWithPort() {
        assertEquals("http://localhost:8080/x", SharedUrl.soleUrlOrNull("http://localhost:8080/x"))
    }

    @Test
    fun soleUrlOrNull_rejectsUrlInsideProse() {
        assertNull(SharedUrl.soleUrlOrNull("look at this https://example.com"))
        assertNull(SharedUrl.soleUrlOrNull("Title\nhttps://example.com"))
    }

    @Test
    fun soleUrlOrNull_rejectsNonWebSchemes() {
        assertNull(SharedUrl.soleUrlOrNull("ftp://example.com/a"))
        assertNull(SharedUrl.soleUrlOrNull("file:///etc/passwd"))
        assertNull(SharedUrl.soleUrlOrNull("content://media/external/images/1"))
    }

    @Test
    fun soleUrlOrNull_rejectsSchemelessHost() {
        assertNull(SharedUrl.soleUrlOrNull("www.example.com"))
    }

    @Test
    fun soleUrlOrNull_rejectsHostWithoutDot() {
        assertNull(SharedUrl.soleUrlOrNull("https://nodot"))
    }

    @Test
    fun soleUrlOrNull_rejectsOverlyLongUrl() {
        val long = "https://example.com/" + "a".repeat(SharedUrl.MAX_URL_LENGTH)
        assertNull(SharedUrl.soleUrlOrNull(long))
    }

    @Test
    fun soleUrlOrNull_rejectsBlank() {
        assertNull(SharedUrl.soleUrlOrNull("   "))
    }

    @Test
    fun looksLikeUrl_matchesSoleUrlDetection() {
        assertTrue(SharedUrl.looksLikeUrl("https://example.com"))
        assertFalse(SharedUrl.looksLikeUrl("Some page title"))
    }
}
