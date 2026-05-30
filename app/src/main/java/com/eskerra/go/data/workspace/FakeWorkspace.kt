package com.eskerra.go.data.workspace

import com.eskerra.go.core.model.Workspace

/**
 * Hardcoded workspace for the UI-only step. No real storage location is involved.
 */
object FakeWorkspace {
    val current: Workspace = Workspace(
        name = "My Notes",
        noteCount = 3,
    )
}
