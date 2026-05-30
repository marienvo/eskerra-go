package com.eskerra.go.data.workspace

import org.junit.Assert.assertFalse
import org.junit.Test

/** JVM check that workspace metadata keys never store credentials. */
class WorkspaceMetadataCredentialsTest {

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
