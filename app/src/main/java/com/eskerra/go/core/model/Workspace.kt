package com.eskerra.go.core.model

/**
 * A single notes workspace. For this UI-only step it carries a display name and
 * a note count; later it will map to a real on-device location.
 */
data class Workspace(
    val name: String,
    val noteCount: Int,
)
