package com.eskerra.go.data.git

import com.eskerra.go.data.workspace.isInvalidRepositoryError
import com.eskerra.go.data.workspace.mapCloneFailure
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitRemoteBranchProbeTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun resolveRemoteBranch_missingFileRemote_mapsToInvalidRepository() {
        val missingUri = TestGitRepos.fileUri(File(temp.root, "does-not-exist.git"))

        val result = GitRemoteBranchProbe.resolveRemoteBranch(missingUri, "main", httpsToken = null)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("unexpected message: $message", isInvalidRepositoryError(message))
        val mapped = mapCloneFailure(result.exceptionOrNull()!!, "main")
        assertTrue(
            "expected InvalidRepository but was ${mapped.error}",
            mapped.error is com.eskerra.go.data.workspace.WorkspaceSetupError.InvalidRepository
        )
    }
}
