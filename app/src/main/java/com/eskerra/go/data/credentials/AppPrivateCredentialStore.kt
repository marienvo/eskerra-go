package com.eskerra.go.data.credentials

import java.io.File

/**
 * PoC credential store. Tokens live in a dedicated app-private directory and are
 * never written to workspace DataStore metadata. Production would add Keystore-backed
 * encryption; this implementation keeps the seam explicit and testable.
 */
class AppPrivateCredentialStore(private val filesDir: File) : CredentialStore {

    private fun tokenFile(relativePath: String): File =
        File(File(filesDir, CREDENTIALS_DIR), "$relativePath.token")

    override suspend fun saveToken(relativePath: String, token: String): Result<Unit> =
        runCatching {
            val file = tokenFile(relativePath)
            file.parentFile?.mkdirs()
            file.writeText(token)
        }

    override suspend fun clear(relativePath: String): Result<Unit> = runCatching {
        tokenFile(relativePath).delete()
    }

    companion object {
        const val CREDENTIALS_DIR = "credentials"
    }
}
