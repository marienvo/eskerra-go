package com.eskerra.go.core.model

/** UI-safe Git working-tree status for the editor header. */
data class GitStatusSummary(
    val state: State,
    val branch: String?,
    val changedCount: Int,
    val message: String
) {
    enum class State {
        Clean,
        Dirty,
        Unavailable,
        Error
    }

    fun formatLabel(): String = when (state) {
        State.Clean -> {
            val branchLabel = branch?.takeIf { it.isNotBlank() } ?: "unknown"
            "$branchLabel - clean"
        }
        State.Dirty -> {
            val branchLabel = branch?.takeIf { it.isNotBlank() } ?: "unknown"
            val fileLabel = if (changedCount == 1) "1 file" else "$changedCount files"
            "$branchLabel - dirty ($fileLabel)"
        }
        State.Unavailable, State.Error -> message
    }

    companion object {
        val unavailable: GitStatusSummary = GitStatusSummary(
            state = State.Unavailable,
            branch = null,
            changedCount = 0,
            message = "Git status unavailable"
        )

        val error: GitStatusSummary = GitStatusSummary(
            state = State.Error,
            branch = null,
            changedCount = 0,
            message = "Git status unavailable"
        )
    }
}
