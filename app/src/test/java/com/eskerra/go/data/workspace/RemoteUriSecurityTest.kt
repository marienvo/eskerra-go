package com.eskerra.go.data.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUriSecurityTest {

    @Test
    fun containsEmbeddedCredentials_detectsHttpsTokenUserinfo() {
        assertTrue(
            RemoteUriSecurity.containsEmbeddedCredentials(
                "https://mysecrettoken@example.com/repo.git"
            )
        )
    }

    @Test
    fun containsEmbeddedCredentials_detectsHttpsUserAndPassword() {
        assertTrue(
            RemoteUriSecurity.containsEmbeddedCredentials(
                "https://user:mysecrettoken@example.com/repo.git"
            )
        )
    }

    @Test
    fun containsEmbeddedCredentials_detectsSshUserinfo() {
        assertTrue(
            RemoteUriSecurity.containsEmbeddedCredentials(
                "ssh://git@example.com/repo.git"
            )
        )
    }

    @Test
    fun containsEmbeddedCredentials_detectsFileAuthorityWithToken() {
        assertTrue(
            RemoteUriSecurity.containsEmbeddedCredentials(
                "file://mysecrettoken@/tmp/repo.git"
            )
        )
    }

    @Test
    fun containsEmbeddedCredentials_detectsFileAuthorityWithUserAndPassword() {
        assertTrue(
            RemoteUriSecurity.containsEmbeddedCredentials(
                "file://user:mysecrettoken@/tmp/repo.git"
            )
        )
    }

    @Test
    fun containsEmbeddedCredentials_allowsFileRemote() {
        assertFalse(
            RemoteUriSecurity.containsEmbeddedCredentials("file:///tmp/example.git")
        )
    }

    @Test
    fun validateNoEmbeddedCredentials_returnsTypedError() {
        val result = RemoteUriSecurity.validateNoEmbeddedCredentials(
            "https://mysecrettoken@example.com/repo.git"
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as WorkspaceSetupException
        assertTrue(error.error is WorkspaceSetupError.CredentialBearingRemoteUri)
        assertFalse(error.error.message().contains("mysecrettoken"))
    }
}
