package com.eskerra.go.data.credentials

import com.eskerra.go.data.workspace.WorkspacePaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeCredentialStoreTest {

    @Test
    fun saveToken_readToken_clear_roundTrip() = runTest {
        val sut = FakeCredentialStore()

        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-one").getOrThrow()
        assertEquals("token-one", sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())

        sut.clear(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow()
        assertNull(sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
    }

    @Test
    fun saveToken_replacesExistingValue() = runTest {
        val sut = FakeCredentialStore()
        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-one").getOrThrow()

        sut.saveToken(WorkspacePaths.DEFAULT_RELATIVE_PATH, "token-two").getOrThrow()

        assertEquals("token-two", sut.readToken(WorkspacePaths.DEFAULT_RELATIVE_PATH).getOrThrow())
        assertEquals(1, sut.tokens.size)
    }
}
