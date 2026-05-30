package com.eskerra.go.data.git

import org.eclipse.jgit.transport.URIish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsTokenCredentialsProviderFactoryTest {

    @Test
    fun credentialsProvider_suppliesTokenWithoutModifyingRemoteUri() {
        val token = "secret-access-token"
        val provider = HttpsTokenCredentialsProviderFactory.credentialsProvider(token)
        val uri = URIish("https://example.com/org/repo.git")
        val uriBefore = uri.toString()

        assertTrue(provider.get(uri))

        assertEquals(uriBefore, uri.toString())
        assertFalse(uri.toString().contains(token))
    }
}
