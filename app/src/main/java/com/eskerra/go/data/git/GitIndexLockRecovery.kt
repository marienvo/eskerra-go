package com.eskerra.go.data.git

import java.io.File

/** Clears a leftover JGit index lock before a new git mutation sequence. */
object GitIndexLockRecovery {

    fun clearStaleLock(workingDir: File): Result<Unit> = runCatching {
        val lock = File(workingDir, ".git/index.lock")
        if (lock.exists()) {
            lock.delete()
        }
    }
}
