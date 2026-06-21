package com.eskerra.go.data.git

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitIndexLockRecoveryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo = JGitWorkspaceRepository()

    @Test
    fun clearStaleLock_removesIndexLockFile() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()
        val lock = File(dir, ".git/index.lock").apply { writeText("stale") }

        GitIndexLockRecovery.clearStaleLock(dir).getOrThrow()

        assertFalse(lock.exists())
    }

    @Test
    fun clearStaleLock_failsWhenLockCannotBeRemoved() {
        val dir = temp.newFolder("workspace")
        repo.initOrOpen(dir).getOrThrow()
        val lock = File(dir, ".git/index.lock").apply {
            mkdirs()
            File(this, "nested").writeText("held")
        }

        val result = GitIndexLockRecovery.clearStaleLock(dir)

        assertTrue(result.isFailure)
        assertTrue(lock.exists())
    }
}
