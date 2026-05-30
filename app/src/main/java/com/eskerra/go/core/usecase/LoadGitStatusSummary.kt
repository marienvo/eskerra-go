package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.GitStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.WorkspaceGitRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File

/** Maps [WorkspaceGitRepository.status] to a UI-safe [GitStatusSummary]. */
class LoadGitStatusSummary(private val gitRepository: WorkspaceGitRepository) {

    operator fun invoke(config: WorkspaceConfig, filesDir: File): GitStatusSummary {
        val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
        if (workspaceResult.isFailure) {
            return GitStatusSummary.unavailable
        }

        val workspaceDir = workspaceResult.getOrThrow()
        if (!workspaceDir.isDirectory || !WorkspacePaths.isValidGitWorkspace(workspaceDir)) {
            return GitStatusSummary.unavailable
        }

        return gitRepository.status(workspaceDir).fold(
            onSuccess = { status ->
                if (status.hasUncommittedChanges) {
                    GitStatusSummary(
                        state = GitStatusSummary.State.Dirty,
                        branch = status.branch,
                        changedCount = status.changedPaths.size,
                        message = ""
                    )
                } else {
                    GitStatusSummary(
                        state = GitStatusSummary.State.Clean,
                        branch = status.branch,
                        changedCount = 0,
                        message = ""
                    )
                }
            },
            onFailure = { GitStatusSummary.error }
        )
    }
}
