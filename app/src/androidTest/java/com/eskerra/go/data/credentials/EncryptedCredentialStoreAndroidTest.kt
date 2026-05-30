package com.eskerra.go.data.credentials

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedCredentialStoreAndroidTest {

    @Test
    fun keystoreBackedStore_roundTripsTokenWithoutPlaintextOnDisk() = runBlocking {
        val filesDir = File.createTempFile("credentials", null).apply {
            delete()
            mkdirs()
        }
        try {
            val sut = EncryptedCredentialStore(
                filesDir = filesDir,
                tokenCipher = AndroidKeystoreTokenCipher()
            )
            val token = "device-secret-token"

            sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, token).getOrThrow()
            assertEquals(token, sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())

            val tokenFile = File(
                filesDir,
                "${EncryptedCredentialStore.CREDENTIALS_DIR}/${WorkspacePaths.DEFAULT_RELATIVE_PATH}.token"
            )
            assertTrue(tokenFile.isFile)
            assertFalse(tokenFile.readText().contains(token))

            sut.clear(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow()
            assertNull(sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
