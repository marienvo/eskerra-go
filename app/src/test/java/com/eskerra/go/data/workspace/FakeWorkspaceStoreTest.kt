package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.WorkspaceConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeWorkspaceStoreTest {

    private val store = FakeWorkspaceStore()

    private val sampleConfig = WorkspaceConfig(
        name = "My Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = null,
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun read_returnsNullWhenUnset() = runTest {
        assertNull(store.read())
    }

    @Test
    fun saveAndRead_roundTrip() = runTest {
        store.save(sampleConfig)

        assertEquals(sampleConfig, store.read())
    }

    @Test
    fun clear_removesConfig() = runTest {
        store.save(sampleConfig)
        store.clear()

        assertNull(store.read())
    }
}
