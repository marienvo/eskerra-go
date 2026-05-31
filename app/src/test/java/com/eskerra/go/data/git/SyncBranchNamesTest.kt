package com.eskerra.go.data.git

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncBranchNamesTest {

    @Test
    fun reconcileLegacyDefault_masterOnlyOnRemote_returnsMain() {
        val effective = SyncBranchNames.reconcileLegacyDefault("master") { remote ->
            remote == "main"
        }
        assertEquals("main", effective)
    }

    @Test
    fun reconcileLegacyDefault_bothOnRemote_keepsMaster() {
        val effective = SyncBranchNames.reconcileLegacyDefault("master") { remote ->
            remote == "master" || remote == "main"
        }
        assertEquals("master", effective)
    }

    @Test
    fun reconcileLegacyDefault_nonLegacyBranch_unchanged() {
        val effective = SyncBranchNames.reconcileLegacyDefault("develop") { true }
        assertEquals("develop", effective)
    }
}
