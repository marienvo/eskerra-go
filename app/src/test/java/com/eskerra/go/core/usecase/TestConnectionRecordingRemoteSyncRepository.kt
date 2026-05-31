package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import java.io.File

/** Records sync mutating operations during a connection test. */
class TestConnectionRecordingRemoteSyncRepository(
    private val delegate: RemoteSyncRepository = JGitRemoteSyncRepository()
) : RemoteSyncRepository by delegate {

    var configureOriginCalled = false
        private set
    var fetchCalled = false
        private set
    var probeCalled = false
        private set
    var stageInboxCalled = false
        private set
    var commitCalled = false
        private set
    var pushCalled = false
        private set
    var fastForwardCalled = false
        private set

    override fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit> {
        configureOriginCalled = true
        return delegate.configureSanitizedOrigin(workingDir, remoteUri)
    }

    override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> {
        fetchCalled = true
        return delegate.fetch(workingDir, httpsToken)
    }

    override fun probeRemoteConnection(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<Unit> {
        probeCalled = true
        return delegate.probeRemoteConnection(remoteUri, branch, httpsToken)
    }

    override fun stageInboxChanges(workingDir: File): Result<Unit> {
        stageInboxCalled = true
        return delegate.stageInboxChanges(workingDir)
    }

    override fun commitStaged(workingDir: File, message: String): Result<String> {
        commitCalled = true
        return delegate.commitStaged(workingDir, message)
    }

    override fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit> {
        pushCalled = true
        return delegate.push(workingDir, branch, httpsToken)
    }

    override fun fastForwardToRemote(workingDir: File, branch: String): Result<Unit> {
        fastForwardCalled = true
        return delegate.fastForwardToRemote(workingDir, branch)
    }
}
