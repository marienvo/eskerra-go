package com.eskerra.go.data.credentials

import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedCredentialStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): EncryptedCredentialStore = EncryptedCredentialStore(
        filesDir = temp.newFolder("files"),
        tokenCipher = AesGcmTokenCipher(AesGcmTokenCipher.randomKeyBytes())
    )

    @Test
    fun saveToken_readToken_returnsSavedValue() = runTest {
        val sut = store()

        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-one").getOrThrow()

        assertEquals("token-one", sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
    }

    @Test
    fun readToken_returnsNullAfterClear() = runTest {
        val sut = store()
        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-one").getOrThrow()

        sut.clear(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow()

        assertNull(sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
    }

    @Test
    fun saveToken_replacesExistingValue() = runTest {
        val sut = store()
        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-one").getOrThrow()

        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-two").getOrThrow()

        assertEquals("token-two", sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
    }

    @Test
    fun saveToken_doesNotWritePlaintextTokenToDisk() = runTest {
        val filesDir = temp.newFolder("files")
        val sut = EncryptedCredentialStore(
            filesDir = filesDir,
            tokenCipher = AesGcmTokenCipher(AesGcmTokenCipher.randomKeyBytes())
        )
        val token = "secret-access-token"

        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, token).getOrThrow()

        val tokenFile = File(
            filesDir,
            "${EncryptedCredentialStore.CREDENTIALS_DIR}/${WorkspacePaths.DEFAULT_RELATIVE_PATH}.token"
        )
        assertTrue(tokenFile.isFile)
        assertFalse(tokenFile.readText().contains(token))
        assertFalse(String(tokenFile.readBytes(), Charsets.UTF_8).contains(token))
    }
}
