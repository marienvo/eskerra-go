package com.eskerra.go.data.git

import com.eskerra.go.core.model.GitWorkspaceStatus
import com.eskerra.go.core.model.MergeOutcome
import com.eskerra.go.core.model.RemoteBranchComparison
import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.core.model.SyncStatusState
import com.eskerra.go.core.model.SyncStatusSummary
import com.eskerra.go.core.repository.RemoteSyncRepository
import com.eskerra.go.data.notes.MarkdownNoteScanner
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.TreeWalk

/**
 * JGit-backed [RemoteSyncRepository] for manual sync.
 *
 * Network operations accept an optional in-memory [httpsToken]; when null, the
 * default [transportConfigCallback] from [gitRepository] is used (for `file://`).
 */
class JGitRemoteSyncRepository(
    private val gitRepository: WorkspaceGitRepository = JGitWorkspaceRepository()
) : RemoteSyncRepository {

    override fun status(workingDir: File): Result<GitWorkspaceStatus> =
        gitRepository.status(workingDir)

    override fun readStagedPaths(workingDir: File): Result<Set<String>> =
        GitIndexInspector.readStagedPaths(workingDir)

    override fun requiresManualIntervention(workingDir: File): Boolean =
        GitRepoStateInspector.requiresManualIntervention(workingDir)

    override fun partitionChanges(changedPaths: Set<String>): SyncChangePartition =
        SyncPathClassifier.partition(changedPaths)

    override fun stageInboxChanges(workingDir: File): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            val inboxPattern = "${MarkdownNoteScanner.INBOX_DIRECTORY}/"
            git.add().addFilepattern(inboxPattern).call()
            git.add().addFilepattern(inboxPattern).setUpdate(true).call()
        }
    }

    override fun stagePaths(workingDir: File, relativePaths: Set<String>): Result<Unit> =
        runCatching {
            if (relativePaths.isEmpty()) return@runCatching
            GitChangeStager.stagePaths(workingDir, relativePaths)
        }

    override fun stageAllChanges(workingDir: File): Result<Unit> =
        gitRepository.stageAll(workingDir)

    override fun abortInProgressOperation(workingDir: File): Result<Unit> = runCatching {
        val gitDir = File(workingDir, ".git")
        val rebaseInProgress = File(gitDir, "rebase-merge").isDirectory ||
            File(gitDir, "rebase-apply").isDirectory
        Git.open(workingDir).use { git ->
            if (rebaseInProgress) {
                git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
            } else {
                // Hard reset clears MERGE_HEAD/CHERRY_PICK_HEAD/REVERT_HEAD and the
                // partially-applied working tree, returning to the last good commit.
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call()
            }
        }
    }

    override fun commitStaged(workingDir: File, message: String): Result<String> =
        gitRepository.commit(workingDir, message)

    override fun mergeRemote(
        workingDir: File,
        branch: String,
        conflictLabel: String
    ): Result<MergeOutcome> {
        val merge = runCatching {
            Git.open(workingDir).use { git ->
                val repository = git.repository
                val remoteRef = requireRemoteRef(repository, branch)
                val mergeResult = git.merge()
                    .include(remoteRef.objectId)
                    .setStrategy(MergeStrategy.RECURSIVE)
                    .setCommit(false)
                    .call()
                val copies = if (mergeResult.mergeStatus == MergeResult.MergeStatus.CONFLICTING) {
                    resolveConflictsRemoteWins(
                        git = git,
                        repository = repository,
                        remoteId = remoteRef.objectId,
                        conflictPaths = mergeResult.conflicts?.keys.orEmpty(),
                        conflictLabel = conflictLabel,
                        workingDir = workingDir
                    )
                } else {
                    emptyList()
                }
                MergeAttempt(mergeResult.mergeStatus, copies)
            }
        }.getOrElse { return Result.failure(it) }

        return when (merge.status) {
            MergeResult.MergeStatus.ALREADY_UP_TO_DATE ->
                Result.success(MergeOutcome(merged = false))
            MergeResult.MergeStatus.FAST_FORWARD ->
                Result.success(MergeOutcome(merged = true))
            MergeResult.MergeStatus.MERGED,
            MergeResult.MergeStatus.MERGED_NOT_COMMITTED,
            MergeResult.MergeStatus.CONFLICTING ->
                // Records a two-parent merge commit (JGit reads MERGE_HEAD from disk).
                gitRepository.commit(workingDir, MERGE_COMMIT_MESSAGE)
                    .map { MergeOutcome(merged = true, conflictCopies = merge.conflictCopies) }
            else ->
                Result.failure(IllegalStateException("merge failed: ${merge.status}"))
        }
    }

    /**
     * Resolves each conflicting path remote-wins: saves the local version to a sidecar
     * copy, then writes the remote version (or removes the file when the remote deleted
     * it). Returns the repo-relative copy paths created.
     */
    private fun resolveConflictsRemoteWins(
        git: Git,
        repository: Repository,
        remoteId: ObjectId,
        conflictPaths: Set<String>,
        conflictLabel: String,
        workingDir: File
    ): List<String> {
        val copies = mutableListOf<String>()
        for (path in conflictPaths) {
            val oursBytes = readBlobBytes(repository, "HEAD", path)
            if (oursBytes != null) {
                val copyPath = conflictCopyPath(path, conflictLabel)
                writeWorkspaceFile(workingDir, copyPath, oursBytes)
                git.add().addFilepattern(copyPath).call()
                copies += copyPath
            }
            val theirsBytes = readBlobBytes(repository, remoteId.name, path)
            if (theirsBytes != null) {
                writeWorkspaceFile(workingDir, path, theirsBytes)
                git.add().addFilepattern(path).call()
            } else {
                // Remote deleted the file; remote-wins means the deletion stands.
                git.rm().addFilepattern(path).call()
            }
        }
        return copies
    }

    private fun readBlobBytes(repository: Repository, revision: String, path: String): ByteArray? {
        val commitId = repository.resolve(revision) ?: return null
        RevWalk(repository).use { revWalk ->
            val tree = revWalk.parseCommit(commitId).tree
            TreeWalk.forPath(repository, path, tree)?.use { treeWalk ->
                return repository.open(treeWalk.getObjectId(0)).bytes
            }
        }
        return null
    }

    private fun writeWorkspaceFile(workingDir: File, relativePath: String, bytes: ByteArray) {
        val base = workingDir.canonicalFile
        val target = File(base, relativePath).canonicalFile
        require(target.toPath().startsWith(base.toPath())) {
            "resolved path escapes workspace: $relativePath"
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }

    /** Inserts " ([label])" before the file extension, e.g. `note (conflict …).md`. */
    private fun conflictCopyPath(path: String, label: String): String {
        val normalized = path.replace('\\', '/')
        val dir = normalized.substringBeforeLast('/', "")
        val name = normalized.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        val baseName = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        val copyName = "$baseName ($label)$extension"
        return if (dir.isEmpty()) copyName else "$dir/$copyName"
    }

    private data class MergeAttempt(
        val status: MergeResult.MergeStatus,
        val conflictCopies: List<String>
    )

    override fun fetch(workingDir: File, httpsToken: String?): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            git.fetch()
                .setRemote(ORIGIN)
                .withTransport(httpsToken)
                .call()
        }
    }

    override fun ensureLocalBranch(
        workingDir: File,
        branch: String,
        httpsToken: String?
    ): Result<String> = GitLocalBranchAlignment.ensure(workingDir, branch, httpsToken)

    override fun compareWithRemote(
        workingDir: File,
        branch: String
    ): Result<RemoteBranchComparison> = runCatching {
        Git.open(workingDir).use { git ->
            compareWithRemote(git.repository, branch)
        }
    }

    override fun fastForwardToRemote(workingDir: File, branch: String): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            val repository = git.repository
            val remoteRef = requireRemoteRef(repository, branch)
            val comparison = compareWithRemote(repository, branch)
            if (comparison.isEqual) return@runCatching
            if (!comparison.localIsAncestorOfRemote) {
                error("not fast-forwardable")
            }

            git.checkout().setName(branch).call()
            val mergeResult = git.merge()
                .include(remoteRef.objectId)
                .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                .call()
            if (mergeResult.mergeStatus != MergeResult.MergeStatus.FAST_FORWARD) {
                error("not fast-forwardable: ${mergeResult.mergeStatus}")
            }
        }
    }

    override fun push(workingDir: File, branch: String, httpsToken: String?): Result<Unit> =
        runCatching {
            Git.open(workingDir).use { git ->
                val results = git.push()
                    .setRemote(ORIGIN)
                    .add(branch)
                    .withTransport(httpsToken)
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

    override fun configureSanitizedOrigin(workingDir: File, remoteUri: String): Result<Unit> =
        gitRepository.configureSanitizedOrigin(workingDir, remoteUri)

    override fun probeRemoteConnection(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<Unit> = runCatching {
        GitRemoteBranchProbe.resolveRemoteBranch(remoteUri, branch, httpsToken).getOrThrow()
    }

    override fun clearSanitizedOrigin(workingDir: File): Result<Unit> =
        gitRepository.clearSanitizedOrigin(workingDir)

    override fun readOriginUrl(workingDir: File): Result<String?> =
        gitRepository.readOriginUrl(workingDir)

    override fun buildStatusSummary(
        workspaceStatus: GitWorkspaceStatus,
        comparison: RemoteBranchComparison?
    ): SyncStatusSummary {
        val changedCount = workspaceStatus.changedPaths.size
        if (workspaceStatus.hasUncommittedChanges) {
            return SyncStatusSummary(
                state = SyncStatusState.DirtyLocalChanges,
                branch = workspaceStatus.branch,
                changedCount = changedCount,
                aheadCount = comparison?.aheadCount ?: 0,
                behindCount = comparison?.behindCount ?: 0,
                message = "Local changes need commit or cleanup before sync."
            )
        }

        if (comparison == null) {
            return SyncStatusSummary(
                state = SyncStatusState.Clean,
                branch = workspaceStatus.branch,
                changedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                message = "Up to date locally."
            )
        }

        if (comparison.remoteBranchMissing) {
            return SyncStatusSummary(
                state = SyncStatusState.Unavailable,
                branch = workspaceStatus.branch,
                changedCount = 0,
                aheadCount = 0,
                behindCount = 0,
                message = "Remote branch not found."
            )
        }

        val state = when {
            comparison.isDiverged -> SyncStatusState.Diverged
            comparison.aheadCount > 0 && comparison.behindCount > 0 -> SyncStatusState.ConflictRisk
            comparison.aheadCount > 0 -> SyncStatusState.Ahead
            comparison.behindCount > 0 -> SyncStatusState.Behind
            else -> SyncStatusState.Clean
        }

        val message = when (state) {
            SyncStatusState.Ahead -> "Local branch is ahead of remote."
            SyncStatusState.Behind -> "Remote has changes to pull."
            SyncStatusState.Diverged,
            SyncStatusState.ConflictRisk ->
                "Local and remote histories have diverged."
            else -> "Up to date."
        }

        return SyncStatusSummary(
            state = state,
            branch = workspaceStatus.branch,
            changedCount = 0,
            aheadCount = comparison.aheadCount,
            behindCount = comparison.behindCount,
            message = message
        )
    }

    private fun compareWithRemote(repository: Repository, branch: String): RemoteBranchComparison {
        val localRef = repository.exactRef("refs/heads/$branch")
            ?: error("local branch not found: $branch")
        val remoteRef = repository.exactRef("refs/remotes/$ORIGIN/$branch")

        if (remoteRef == null) {
            return RemoteBranchComparison(
                aheadCount = 0,
                behindCount = 0,
                isEqual = false,
                localIsAncestorOfRemote = false,
                remoteIsAncestorOfLocal = false,
                isDiverged = false,
                remoteBranchMissing = true
            )
        }

        val localId = localRef.objectId
        val remoteId = remoteRef.objectId
        if (localId == remoteId) {
            return RemoteBranchComparison(
                aheadCount = 0,
                behindCount = 0,
                isEqual = true,
                localIsAncestorOfRemote = true,
                remoteIsAncestorOfLocal = true,
                isDiverged = false
            )
        }

        RevWalk(repository).use { revWalk ->
            val localCommit = revWalk.parseCommit(localId)
            val remoteCommit = revWalk.parseCommit(remoteId)

            revWalk.reset()
            revWalk.revFilter = org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
            revWalk.markStart(localCommit)
            revWalk.markStart(remoteCommit)
            val mergeBase = revWalk.next()

            val localIsAncestor = mergeBase?.id == localCommit.id
            val remoteIsAncestor = mergeBase?.id == remoteCommit.id
            val isDiverged = !localIsAncestor && !remoteIsAncestor

            val aheadCount = when {
                remoteIsAncestor ->
                    countCommitsBetween(revWalk, remoteCommit, localCommit)
                isDiverged ->
                    countCommitsBetween(revWalk, mergeBase, localCommit)
                else -> 0
            }
            val behindCount = when {
                localIsAncestor ->
                    countCommitsBetween(revWalk, localCommit, remoteCommit)
                isDiverged ->
                    countCommitsBetween(revWalk, mergeBase, remoteCommit)
                else -> 0
            }

            return RemoteBranchComparison(
                aheadCount = aheadCount,
                behindCount = behindCount,
                isEqual = false,
                localIsAncestorOfRemote = localIsAncestor,
                remoteIsAncestorOfLocal = remoteIsAncestor,
                isDiverged = isDiverged
            )
        }
    }

    private fun countCommitsBetween(revWalk: RevWalk, base: RevCommit?, tip: RevCommit): Int {
        if (base?.id == tip.id) return 0
        revWalk.reset()
        revWalk.markStart(tip)
        base?.let { revWalk.markUninteresting(it) }
        var count = 0
        for (@Suppress("UNUSED_PARAMETER") commit in revWalk) {
            count++
        }
        return count
    }

    private fun requireRemoteRef(repository: Repository, branch: String): Ref =
        repository.exactRef("refs/remotes/$ORIGIN/$branch")
            ?: error("remote tracking branch not found: origin/$branch")

    private fun <C : TransportCommand<*, *>> C.withTransport(httpsToken: String?): C {
        val callback = httpsToken?.let {
            HttpsTokenCredentialsProviderFactory.transportConfigCallback(it)
        }
        callback?.let { setTransportConfigCallback(it) }
        return this
    }

    private companion object {
        const val ORIGIN = "origin"
        const val MERGE_COMMIT_MESSAGE = "Merge remote changes from Eskerra Go"
    }
}
