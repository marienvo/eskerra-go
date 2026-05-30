package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.GitStatusSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.WorkspaceGitStatusRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Maps [WorkspaceGitStatusRepository.status] to a UI-safe [GitStatusSummary]. */
class LoadGitStatusSummary(
    private val gitRepository: WorkspaceGitStatusRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(config: WorkspaceConfig, filesDir: File): GitStatusSummary =
        withContext(dispatcher) {
            load(config, filesDir)
        }

    private fun load(config: WorkspaceConfig, filesDir: File): GitStatusSummary {
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
