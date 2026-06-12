package com.eskerra.go.data.git

import com.eskerra.go.core.model.GitWorkspaceStatus
import java.io.File

/** [WorkspaceGitRepository] that fails [status] after a successful [initOrOpen]. */
class StatusFailingGitRepository(
    private val delegate: WorkspaceGitRepository = JGitWorkspaceRepository(),
    private val statusFailure: Throwable = RuntimeException("status failed")
) : WorkspaceGitRepository {

    override fun initOrOpen(workingDir: File): Result<Unit> = delegate.initOrOpen(workingDir)

    override fun cloneFrom(
        remoteUri: String,
        workingDir: File,
        branch: String?,
        httpsToken: String?
    ): Result<Unit> = delegate.cloneFrom(remoteUri, workingDir, branch, httpsToken)

    override fun resolveCloneBranch(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<String> = delegate.resolveCloneBranch(remoteUri, branch, httpsToken)

    override fun status(workingDir: File): Result<GitWorkspaceStatus> =
        Result.failure(statusFailure)

    override fun writeFile(workingDir: File, relativePath: String, content: String): Result<Unit> =
        delegate.writeFile(workingDir, relativePath, content)

    override fun deleteFile(workingDir: File, relativePath: String): Result<Unit> =
        delegate.deleteFile(workingDir, relativePath)

    override fun stageAll(workingDir: File): Result<Unit> = delegate.stageAll(workingDir)

    override fun commit(workingDir: File, message: String): Result<String> =
        delegate.commit(workingDir, message)

    override fun fetch(workingDir: File): Result<Unit> = delegate.fetch(workingDir)

    override fun pullFastForwardOnly(workingDir: File): Result<Unit> =
        delegate.pullFastForwardOnly(workingDir)

    override fun push(workingDir: File): Result<Unit> = delegate.push(workingDir)

    override fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit> =
        delegate.configureSanitizedOrigin(workingDir, remoteUri)

    override fun clearSanitizedOrigin(workingDir: File): Result<Unit> =
        delegate.clearSanitizedOrigin(workingDir)

    override fun readOriginUrl(workingDir: File): Result<String?> =
        delegate.readOriginUrl(workingDir)
}
