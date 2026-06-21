package com.eskerra.go.data.git

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide lock serializing every JGit working-tree mutation.
 *
 * Manual inbox sync and podcast auto-sync both acquire this single mutex so their
 * stage/commit/fetch/fast-forward/push sequences never interleave on the same
 * repository. A single instance must be shared between all sync use cases.
 */
class GitSyncMutex {
    val mutex = Mutex()
}
