package com.eskerra.go.data.git

import com.eskerra.go.core.model.GitWorkspaceStatus
import java.io.File

/**
 * Records clone parameters for HTTPS setup tests without requiring network access.
 */
class CredentialRecordingGitRepository(
    private val delegate: WorkspaceGitRepository = JGitWorkspaceRepository()
) : WorkspaceGitRepository {

    var lastCloneRemoteUri: String? = null
        private set
    var lastCloneHttpsToken: String? = null
        private set
    var lastCloneBranch: String? = null
        private set

    /** When set, HTTPS clones delegate to this local bare repo instead of the HTTPS URL. */
    var simulatedLocalRemoteUri: String? = null

    var cloneFailure: Throwable? = null

    override fun initOrOpen(workingDir: File): Result<Unit> = delegate.initOrOpen(workingDir)

    override fun cloneFrom(
        remoteUri: String,
        workingDir: File,
        branch: String?,
        httpsToken: String?
    ): Result<Unit> {
        lastCloneRemoteUri = remoteUri
        lastCloneHttpsToken = httpsToken
        lastCloneBranch = branch
        cloneFailure?.let { return Result.failure(it) }
        val effectiveUri = if (httpsToken != null && simulatedLocalRemoteUri != null) {
            simulatedLocalRemoteUri!!
        } else {
            remoteUri
        }
        return delegate.cloneFrom(effectiveUri, workingDir, branch, httpsToken)
    }

    override fun status(workingDir: File): Result<GitWorkspaceStatus> = delegate.status(workingDir)

    override fun writeFile(workingDir: File, relativePath: String, content: String): Result<Unit> =
        delegate.writeFile(workingDir, relativePath, content)

    override fun stageAll(workingDir: File): Result<Unit> = delegate.stageAll(workingDir)

    override fun commit(workingDir: File, message: String): Result<String> =
        delegate.commit(workingDir, message)

    override fun fetch(workingDir: File): Result<Unit> = delegate.fetch(workingDir)

    override fun pullFastForwardOnly(workingDir: File): Result<Unit> =
        delegate.pullFastForwardOnly(workingDir)

    override fun push(workingDir: File): Result<Unit> = delegate.push(workingDir)
}
