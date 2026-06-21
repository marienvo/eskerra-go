package com.eskerra.go.data.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSetupErrorTest {

    @Test
    fun unsupportedRemoteScheme_hasExplicitMessage() {
        val message = WorkspaceSetupError.UnsupportedRemoteScheme.message()
        assertTrue(message.contains("https://"))
        assertTrue(message.contains("file://"))
    }

    @Test
    fun branchNotFound_includesBranchName() {
        assertTrue(
            WorkspaceSetupError.BranchNotFound("develop").message()
                .contains("develop")
        )
    }

    @Test
    fun mapCloneFailure_mapsRefErrorsToBranchNotFound() {
        val error = mapCloneFailure(
            RuntimeException("Ref develop cannot be resolved"),
            branch = "develop"
        )
        assertTrue(error.error is WorkspaceSetupError.BranchNotFound)
    }

    @Test
    fun mapCloneFailure_doesNotMapGenericNotFoundToBranchNotFound() {
        val error = mapCloneFailure(
            RuntimeException("No such file or directory: /missing/remote.git"),
            branch = "main"
        )
        assertTrue(error.error is WorkspaceSetupError.InvalidRepository)
    }

    @Test
    fun mapCloneFailure_mapsAuthErrorsWithoutRetainingRawDetail() {
        val rawDetail = "Authentication failed for secret-token@example.com"
        val error = mapCloneFailure(
            RuntimeException(rawDetail),
            branch = "main"
        )
        assertTrue(error.error is WorkspaceSetupError.AuthenticationFailed)
        assertFalse(error.toString().contains("secret-token"))
        assertFalse(error.error.message().contains("secret-token"))
    }

    @Test
    fun credentialBearingRemoteUri_hasStableMessageWithoutUrl() {
        val message = WorkspaceSetupError.CredentialBearingRemoteUri.message()
        assertTrue(message.contains("username"))
        assertFalse(message.contains("@"))
    }

    @Test
    fun securitySensitiveErrorsDoNotRetainRawDetailFields() {
        val rawDetail = "/tmp/private/token-secret.git"
        val errors = listOf(
            WorkspaceSetupError.InvalidRepository,
            WorkspaceSetupError.AuthenticationFailed,
            WorkspaceSetupError.CloneFailed,
            WorkspaceSetupError.InitFailed,
            WorkspaceSetupError.StorageFailed,
            WorkspaceSetupError.MetadataSaveFailed,
            WorkspaceSetupError.CredentialSaveFailed
        )

        errors.forEach { error ->
            val message = error.message()
            assertFalse("Message must not expose raw detail: $message", message.contains(rawDetail))
            assertFalse("Message must not expose secret text: $message", message.contains("secret"))
        }
    }

    @Test
    fun isBranchRefError_rejectsGenericPathNotFound() {
        assertFalse(isBranchRefError("No such file or directory", branch = "main"))
    }

    @Test
    fun mapCloneFailure_mapsRemoteBranchNotFoundToBranchNotFound() {
        val error = mapCloneFailure(
            RuntimeException("remote branch not found: main"),
            branch = "master"
        )
        assertTrue(error.error is WorkspaceSetupError.BranchNotFound)
        assertTrue(error.error.message().contains("main"))
    }

    @Test
    fun mapCloneFailure_mapsRemoteUnavailableErrors() {
        val error = mapCloneFailure(
            RuntimeException("Connection timed out: connect"),
            branch = "main"
        )
        assertTrue(error.error is WorkspaceSetupError.RemoteUnavailable)
    }

    @Test
    fun mapCloneFailure_prefersInvalidRepositoryOverRemoteUnavailableForMissingFileRemote() {
        val error = mapCloneFailure(
            RuntimeException(
                "unable to access 'file:///tmp/missing.git': No such file or directory"
            ),
            branch = "main"
        )
        assertTrue(
            "expected InvalidRepository but was ${error.error}",
            error.error is WorkspaceSetupError.InvalidRepository
        )
    }

    @Test
    fun authenticationFailed_hasStableMessage() {
        val message = WorkspaceSetupError.AuthenticationFailed.message()
        assertTrue(message == "Authentication failed.")
        assertFalse(message.contains("@"))
    }

    @Test
    fun isInvalidRepositoryError_matchesMissingPath() {
        assertTrue(isInvalidRepositoryError("No such file or directory: /tmp/missing.git"))
    }
}
