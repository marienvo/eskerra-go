package com.eskerra.go.data.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RemoteRefUpdate

/**
 * JGit-backed [WorkspaceGitRepository] for the Step 2 spike.
 *
 * Every operation opens the repository, performs a single explicit action, and
 * closes it again. Failures are returned as [Result.failure] wrapping the
 * underlying JGit/IO exception; nothing is logged or swallowed.
 *
 * The optional [transportConfigCallback] is the single, documented seam where a
 * future HTTPS/SSH credential provider would plug in. For the spike it is left
 * null (a no-op), because all remotes are local `file://` bare repositories that
 * need no authentication.
 *
 * @param identity committer/author identity used for commits. Set explicitly so
 *   commits are deterministic across host JVM and Android device.
 * @param transportConfigCallback no-op auth/transport seam; null for `file://`.
 */
class JGitWorkspaceRepository(
    private val identity: PersonIdent = PersonIdent("Eskerra Go Spike", "spike@eskerra.local"),
    private val transportConfigCallback: TransportConfigCallback? = null
) : WorkspaceGitRepository {

    override fun initOrOpen(workingDir: File): Result<Unit> = runCatching {
        if (!workingDir.exists()) {
            error("workingDir does not exist: $workingDir")
        }
        if (!workingDir.isDirectory) {
            error("workingDir exists but is not a directory: $workingDir")
        }
        if (isGitRepository(workingDir)) {
            Git.open(workingDir).close()
            return@runCatching
        }
        val entries = workingDir.listFiles()
        if (entries != null && entries.isNotEmpty()) {
            error("workingDir is not empty and not a Git repository: $workingDir")
        }
        Git.init().setDirectory(workingDir).call().close()
    }

    override fun cloneFrom(remoteUri: String, workingDir: File, branch: String?): Result<Unit> =
        runCatching {
            if (workingDir.exists()) {
                if (!workingDir.isDirectory) {
                    error("workingDir exists but is not a directory: $workingDir")
                }
                val entries = workingDir.listFiles()
                if (entries != null && entries.isNotEmpty()) {
                    error("clone target is not empty: $workingDir")
                }
            }
            val clone = Git.cloneRepository()
                .setURI(remoteUri)
                .setDirectory(workingDir)
            if (!branch.isNullOrBlank()) {
                clone.setBranch(branch)
            }
            clone.withTransportConfig()
                .call()
                .close()
        }

    override fun status(workingDir: File): Result<GitWorkspaceStatus> = runCatching {
        Git.open(workingDir).use { git ->
            val status = git.status().call()
            val changedPaths = buildSet {
                addAll(status.added)
                addAll(status.changed)
                addAll(status.modified)
                addAll(status.removed)
                addAll(status.missing)
                addAll(status.untracked)
            }
            GitWorkspaceStatus(
                branch = git.repository.branch,
                hasUncommittedChanges = !status.isClean,
                changedPaths = changedPaths
            )
        }
    }

    override fun writeFile(workingDir: File, relativePath: String, content: String): Result<Unit> =
        runCatching {
            require(relativePath.isNotBlank()) { "relativePath must not be blank" }
            require(!File(relativePath).isAbsolute) {
                "relativePath must be relative: $relativePath"
            }
            val segments = relativePath.split('/', '\\')
            require(segments.none { it == ".." }) {
                "relativePath must not contain '..': $relativePath"
            }
            require(segments.none { it == ".git" }) {
                "relativePath must not contain '.git' segment: $relativePath"
            }

            val base = workingDir.canonicalFile
            val target = File(base, relativePath).canonicalFile
            require(target.toPath().startsWith(base.toPath())) {
                "resolved path escapes workingDir: $relativePath"
            }

            target.parentFile?.mkdirs()
            target.writeText(content)
        }

    override fun stageAll(workingDir: File): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            // Stage new and modified files.
            git.add().addFilepattern(".").call()
            // Stage deletions (add alone does not record removals).
            git.add().addFilepattern(".").setUpdate(true).call()
        }
    }

    override fun commit(workingDir: File, message: String): Result<String> = runCatching {
        Git.open(workingDir).use { git ->
            val commit = git.commit()
                .setMessage(message)
                .setAuthor(identity)
                .setCommitter(identity)
                .setSign(false)
                .call()
            commit.name()
        }
    }

    override fun fetch(workingDir: File): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            git.fetch()
                .setRemote(ORIGIN)
                .withTransportConfig()
                .call()
        }
    }

    override fun pullFastForwardOnly(workingDir: File): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            val result = git.pull()
                .setRemote(ORIGIN)
                .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                .withTransportConfig()
                .call()
            if (!result.isSuccessful) {
                error("pull was not a fast-forward: ${result.mergeResult?.mergeStatus}")
            }
        }
    }

    override fun push(workingDir: File): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            val branch = git.repository.branch
            val results = git.push()
                .setRemote(ORIGIN)
                .add(branch)
                .withTransportConfig()
                .call()
            for (pushResult in results) {
                for (update in pushResult.remoteUpdates) {
                    val ok = update.status == RemoteRefUpdate.Status.OK ||
                        update.status == RemoteRefUpdate.Status.UP_TO_DATE
                    if (!ok) {
                        error(
                            "push rejected for ${update.remoteName}: " +
                                "${update.status} ${update.message.orEmpty()}"
                        )
                    }
                }
            }
        }
    }

    private fun isGitRepository(workingDir: File): Boolean = File(workingDir, ".git").exists()

    private fun <C : TransportCommand<*, *>> C.withTransportConfig(): C {
        transportConfigCallback?.let { setTransportConfigCallback(it) }
        return this
    }

    private companion object {
        const val ORIGIN = "origin"
    }
}
