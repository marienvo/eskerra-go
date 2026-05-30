package com.eskerra.go.data.git

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.repository.WorkspaceGitStatusRepository
import java.io.File

/**
 * Minimal Git seam for the Step 2 spike. Operates directly on a caller-provided
 * app-private working directory. Remote operations are proven against a local
 * bare repository (`file://`); a real auth/transport implementation is
 * intentionally deferred.
 *
 * All operations return Kotlin [Result]; failures wrap the underlying JGit/IO
 * exception. No typed error hierarchy is introduced in this spike.
 *
 * The interface is intentionally narrow and free of any Android `Context` so it
 * stays unit-testable on the plain JVM.
 */
interface WorkspaceGitRepository : WorkspaceGitStatusRepository {

    /**
     * Open an existing repository at [workingDir], or run `git init` when
     * [workingDir] is an existing empty directory.
     *
     * Fails when [workingDir] does not exist, is a non-empty directory that is
     * not a Git repo, or is a regular file. Performs no creation, cleanup, or
     * recovery: the directory must already exist.
     */
    fun initOrOpen(workingDir: File): Result<Unit>

    /**
     * Clone [remoteUri] into [workingDir], optionally checking out [branch].
     *
     * For HTTPS remotes, pass [httpsToken] so auth uses an in-memory credential
     * provider instead of embedding credentials in the URL.
     *
     * Fails (writing nothing) when [workingDir] already exists and is not empty.
     */
    fun cloneFrom(
        remoteUri: String,
        workingDir: File,
        branch: String? = null,
        httpsToken: String? = null
    ): Result<Unit>

    /** Read the working tree status of the repository at [workingDir]. */
    override fun status(workingDir: File): Result<GitWorkspaceStatus>

    /**
     * Create or overwrite a file at [relativePath] (relative to the repo root)
     * with [content].
     *
     * Rejects (without writing) when [relativePath] is absolute, blank, or
     * contains a `..` segment, and guarantees the resolved file stays inside
     * [workingDir].
     */
    fun writeFile(workingDir: File, relativePath: String, content: String): Result<Unit>

    /** Stage all changes (additions, modifications, and deletions). */
    fun stageAll(workingDir: File): Result<Unit>

    /** Commit the staged changes and return the new commit id. */
    fun commit(workingDir: File, message: String): Result<String>

    /** Fetch from the configured `origin` remote. */
    fun fetch(workingDir: File): Result<Unit>

    /**
     * Fast-forward-only pull. Fails (without merging) when a non-fast-forward
     * update would be required. No merge or conflict resolution in this spike.
     */
    fun pullFastForwardOnly(workingDir: File): Result<Unit>

    /** Push the current branch to the configured `origin` remote. */
    fun push(workingDir: File): Result<Unit>
}
