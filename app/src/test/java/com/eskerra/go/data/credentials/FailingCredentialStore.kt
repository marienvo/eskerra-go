package com.eskerra.go.data.credentials

/** [CredentialStore] that always fails on save and clear for error-path tests. */
class FailingCredentialStore : CredentialStore {
    var saveFailure: Throwable = RuntimeException("credential save failed")
    var clearFailure: Throwable = RuntimeException("credential clear failed")

    override suspend fun saveToken(relativePath: String, token: String): Result<Unit> =
        Result.failure(saveFailure)

    override suspend fun clear(relativePath: String): Result<Unit> = Result.failure(clearFailure)
}
