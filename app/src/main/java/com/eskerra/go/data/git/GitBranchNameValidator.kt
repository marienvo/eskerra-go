package com.eskerra.go.data.git

import com.eskerra.go.data.workspace.WorkspaceSetupError
import com.eskerra.go.data.workspace.WorkspaceSetupException
import org.eclipse.jgit.lib.Repository

/** Conservative branch-name validation before any JGit remote call. */
object GitBranchNameValidator {

    private const val HEADS_PREFIX = "refs/heads/"

    fun validate(branch: String): Result<Unit> {
        if (branch.trim().isEmpty()) {
            return Result.failure(WorkspaceSetupException(WorkspaceSetupError.BlankBranch))
        }
        if (branch != branch.trim()) {
            return invalidBranch()
        }
        if (branch.any { it.isWhitespace() }) {
            return invalidBranch()
        }
        if (branch.any { it.isISOControl() }) {
            return invalidBranch()
        }
        if (".." in branch) {
            return invalidBranch()
        }
        if ('\\' in branch) {
            return invalidBranch()
        }
        if (branch.startsWith("/")) {
            return invalidBranch()
        }
        if (branch.endsWith("/") || branch.endsWith(".")) {
            return invalidBranch()
        }
        if (branch.endsWith(".lock")) {
            return invalidBranch()
        }
        if (looksLikeRefspec(branch)) {
            return invalidBranch()
        }
        if (containsInvalidRefSyntax(branch)) {
            return invalidBranch()
        }
        if (!Repository.isValidRefName("$HEADS_PREFIX$branch")) {
            return invalidBranch()
        }
        return Result.success(Unit)
    }

    private fun invalidBranch(): Result<Unit> =
        Result.failure(WorkspaceSetupException(WorkspaceSetupError.InvalidBranch))

    private fun looksLikeRefspec(branch: String): Boolean =
        branch.contains(':') || branch.startsWith("refs/") || branch.contains('^')

    private fun containsInvalidRefSyntax(branch: String): Boolean = branch == "@" ||
        branch.contains("@{") ||
        branch.contains('~') ||
        branch.contains('?') ||
        branch.contains('*') ||
        branch.contains('[') ||
        branch.contains('@')
}
