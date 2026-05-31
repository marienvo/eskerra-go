package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import java.io.File

/** Returns workspace status containing an unsafe changed path. */
class UnsafePathRemoteSyncRepository(
    private val delegate: RemoteSyncRepository = JGitRemoteSyncRepository()
) : RemoteSyncRepository by delegate {

    override fun status(workingDir: File): Result<GitWorkspaceStatus> =
        delegate.status(workingDir).map { status ->
            status.copy(changedPaths = status.changedPaths + ".git/HEAD")
        }
}
