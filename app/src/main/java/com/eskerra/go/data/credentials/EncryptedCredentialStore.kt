package com.eskerra.go.data.credentials

import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File

/**
 * Stores workspace tokens as encrypted bytes under app-private storage.
 *
 * Tokens are never written to workspace DataStore metadata or Git config.
 */
class EncryptedCredentialStore(private val filesDir: File, private val tokenCipher: TokenCipher) :
    CredentialStore {

    private fun tokenFile(relativePath: String): File {
        WorkspacePaths.validateRelativePath(relativePath).getOrThrow()
        return File(File(filesDir, CREDENTIALS_DIR), "$relativePath.token")
    }

    override suspend fun saveToken(relativePath: String, token: String): Result<Unit> =
        runCatching {
            val file = tokenFile(relativePath)
            file.parentFile?.mkdirs()
            file.writeBytes(tokenCipher.encrypt(token))
        }

    override suspend fun readToken(relativePath: String): Result<String?> = runCatching {
        val file = tokenFile(relativePath)
        if (!file.isFile) {
            null
        } else {
            tokenCipher.decrypt(file.readBytes())
        }
    }

    override suspend fun clear(relativePath: String): Result<Unit> = runCatching {
        tokenFile(relativePath).delete()
    }

    companion object {
        const val CREDENTIALS_DIR = "credentials"
    }
}
