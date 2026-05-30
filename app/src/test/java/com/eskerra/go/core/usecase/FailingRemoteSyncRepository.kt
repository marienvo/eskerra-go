package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import java.io.File

/** Test double that injects failures at specific sync steps. */
class FailingRemoteSyncRepository(
    private val delegate: RemoteSyncRepository = JGitRemoteSyncRepository(),
    private val fetchError: Throwable? = null,
    private val pushError: Throwable? = null,
    private val probeError: Throwable? = null
) : RemoteSyncRepository by delegate {

    override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> {
        fetchError?.let { return Result.failure(it) }
        return delegate.fetch(workingDir, httpsToken)
    }

    override fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit> {
        pushError?.let { return Result.failure(it) }
        return delegate.push(workingDir, branch, httpsToken)
    }

    override fun probeRemoteConnection(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<Unit> {
        probeError?.let { return Result.failure(it) }
        return delegate.probeRemoteConnection(remoteUri, branch, httpsToken)
    }
}

/** Records whether broad stageAll would have been needed (sync uses inbox-only staging). */
class StageRecordingRemoteSyncRepository(
    private val delegate: RemoteSyncRepository = JGitRemoteSyncRepository()
) : RemoteSyncRepository by delegate {

    var stageInboxCalled = false
        private set
    var stageAllCalled = false
        private set

    override fun stageInboxChanges(workingDir: File): Result<Unit> {
        stageInboxCalled = true
        return delegate.stageInboxChanges(workingDir)
    }
}
