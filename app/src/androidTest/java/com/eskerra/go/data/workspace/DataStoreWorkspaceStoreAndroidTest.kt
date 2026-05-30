package com.eskerra.go.data.workspace

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.model.WorkspaceConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Authoritative DataStore proof: real Preferences DataStore on Android.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreWorkspaceStoreAndroidTest {

    private lateinit var store: DataStoreWorkspaceStore

    private val sampleConfig = WorkspaceConfig(
        name = "On-device Notes",
        relativePath = WorkspacePaths.DEFAULT_RELATIVE_PATH,
        remoteUri = "file:///data/local/tmp/example.git",
        branch = "main",
        setupCompletedAtEpochMs = 1_700_000_000_000L
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = DataStoreWorkspaceStore(context)
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        runBlocking { store.clear() }
    }

    @Test
    fun saveReadAndClear_roundTripOnDevice() = runBlocking {
        assertNull(store.read())

        store.save(sampleConfig)
        assertEquals(sampleConfig, store.read())

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun preferenceKeys_doNotStoreSecrets() {
        val forbidden = setOf("token", "password", "credential", "secret", "auth")
        DataStoreWorkspaceStore.NON_SECRET_PREFERENCE_KEY_NAMES.forEach { key ->
            forbidden.forEach { fragment ->
                assertFalse(
                    "Preference key must not contain '$fragment': $key",
                    key.contains(fragment, ignoreCase = true)
                )
            }
        }
    }
}
