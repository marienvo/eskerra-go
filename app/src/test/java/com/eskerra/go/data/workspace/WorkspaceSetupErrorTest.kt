package com.eskerra.go.data.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSetupErrorTest {

    @Test
    fun unsupportedRemoteScheme_hasExplicitMessage() {
        val message = WorkspaceSetupError.UnsupportedRemoteScheme.message()
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
    fun mapCloneFailure_mapsAuthErrors() {
        val error = mapCloneFailure(
            RuntimeException("Authentication failed for remote"),
            branch = "main"
        )
        assertTrue(error.error is WorkspaceSetupError.AuthenticationFailed)
    }

    @Test
    fun userFacingMessagesDoNotIncludeRawDetails() {
        val rawDetail = "/tmp/private/token-secret.git"
        val errors = listOf(
            WorkspaceSetupError.InvalidRepository(rawDetail),
            WorkspaceSetupError.AuthenticationFailed(rawDetail),
            WorkspaceSetupError.CloneFailed(rawDetail),
            WorkspaceSetupError.InitFailed(rawDetail),
            WorkspaceSetupError.StorageFailed(rawDetail),
            WorkspaceSetupError.MetadataSaveFailed(rawDetail),
            WorkspaceSetupError.CredentialSaveFailed(rawDetail)
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
    fun isInvalidRepositoryError_matchesMissingPath() {
        assertTrue(isInvalidRepositoryError("No such file or directory: /tmp/missing.git"))
    }
}
