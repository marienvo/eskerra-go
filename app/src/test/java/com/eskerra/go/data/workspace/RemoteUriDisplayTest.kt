package com.eskerra.go.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteUriDisplayTest {

    @Test
    fun sanitize_httpsRemote_returnsHostAndPath() {
        val sanitized = RemoteUriDisplay.sanitize("https://github.com/user/notes.git")

        assertEquals("github.com/user/notes.git", sanitized)
    }

    @Test
    fun sanitize_credentialBearingUri_returnsNull() {
        assertNull(
            RemoteUriDisplay.sanitize("https://token:secret@github.com/user/notes.git")
        )
    }

    @Test
    fun sanitize_doesNotIncludeTokenInOutput() {
        val token = "super-secret-token-value"
        val sanitized = RemoteUriDisplay.sanitize("https://github.com/user/notes.git")

        assertFalse(sanitized?.contains(token) == true)
    }
}
