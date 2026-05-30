package com.eskerra.go.data.credentials

/** In-memory [CredentialStore] for JVM tests. */
class FakeCredentialStore : CredentialStore {
    val tokens = mutableMapOf<String, String>()

    override suspend fun saveToken(relativePath: String, token: String): Result<Unit> {
        tokens[relativePath] = token
        return Result.success(Unit)
    }

    override suspend fun readToken(relativePath: String): Result<String?> =
        Result.success(tokens[relativePath])

    override suspend fun clear(relativePath: String): Result<Unit> {
        tokens.remove(relativePath)
        return Result.success(Unit)
    }
}
