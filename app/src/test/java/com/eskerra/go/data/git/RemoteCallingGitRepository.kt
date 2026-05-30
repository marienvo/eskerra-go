package com.eskerra.go.data.git

import com.eskerra.go.core.model.GitWorkspaceStatus
import java.io.File

/**
 * Tracks remote Git operations. Used to prove offline startup paths do not call
 * fetch, pull, clone, or push.
 */
class RemoteCallingGitRepository(
    private val delegate: WorkspaceGitRepository = JGitWorkspaceRepository()
) : WorkspaceGitRepository {

    var cloneCallCount: Int = 0
        private set
    var fetchCallCount: Int = 0
        private set
    var pullCallCount: Int = 0
        private set
    var pushCallCount: Int = 0
        private set

    override fun initOrOpen(workingDir: File): Result<Unit> = delegate.initOrOpen(workingDir)

    override fun cloneFrom(
        remoteUri: String,
        workingDir: File,
        branch: String?,
        httpsToken: String?
    ): Result<Unit> {
        cloneCallCount += 1
        return Result.failure(IllegalStateException("clone should not run offline"))
    }

    override fun resolveCloneBranch(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<String> = delegate.resolveCloneBranch(remoteUri, branch, httpsToken)

    override fun status(workingDir: File): Result<GitWorkspaceStatus> = delegate.status(workingDir)

    override fun writeFile(workingDir: File, relativePath: String, content: String): Result<Unit> =
        delegate.writeFile(workingDir, relativePath, content)

    override fun stageAll(workingDir: File): Result<Unit> = delegate.stageAll(workingDir)

    override fun commit(workingDir: File, message: String): Result<String> =
        delegate.commit(workingDir, message)

    override fun fetch(workingDir: File): Result<Unit> {
        fetchCallCount += 1
        return Result.failure(IllegalStateException("fetch should not run offline"))
    }

    override fun pullFastForwardOnly(workingDir: File): Result<Unit> {
        pullCallCount += 1
        return Result.failure(IllegalStateException("pull should not run offline"))
    }

    override fun push(workingDir: File): Result<Unit> {
        pushCallCount += 1
        return Result.failure(IllegalStateException("push should not run offline"))
    }

    override fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit> =
        delegate.configureSanitizedOrigin(workingDir, remoteUri)

    override fun clearSanitizedOrigin(workingDir: File): Result<Unit> =
        delegate.clearSanitizedOrigin(workingDir)

    override fun readOriginUrl(workingDir: File): Result<String?> =
        delegate.readOriginUrl(workingDir)
}
