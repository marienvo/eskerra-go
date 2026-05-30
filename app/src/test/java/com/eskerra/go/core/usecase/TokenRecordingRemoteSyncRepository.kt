package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.git.JGitRemoteSyncRepository
import java.io.File

/** Records in-memory HTTPS tokens passed to fetch/push for sync security tests. */
class TokenRecordingRemoteSyncRepository(
    private val delegate: RemoteSyncRepository = JGitRemoteSyncRepository()
) : RemoteSyncRepository by delegate {

    var lastFetchToken: String? = null
        private set
    var lastPushToken: String? = null
        private set

    override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> {
        lastFetchToken = httpsToken
        return delegate.fetch(workingDir, httpsToken)
    }

    override fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit> {
        lastPushToken = httpsToken
        return delegate.push(workingDir, branch, httpsToken)
    }
}
