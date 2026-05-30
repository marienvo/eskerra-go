package com.eskerra.go.data.git

import java.io.File
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportCommand

/**
 * Aligns the local checkout with a configured sync branch using JGit only.
 *
 * When the branch already exists locally, checks it out. Otherwise fetches from
 * [originRemote] and creates a tracking branch from [originRemote]/[branch].
 */
internal object GitLocalBranchAlignment {

    const val ORIGIN_REMOTE = "origin"

    fun ensure(
        workingDir: File,
        branch: String,
        httpsToken: String?,
        fetchIfNeeded: Boolean = true
    ): Result<String> = runCatching {
        Git.open(workingDir).use { git ->
            val repository = git.repository
            if (fetchIfNeeded) {
                git.fetch()
                    .setRemote(ORIGIN_REMOTE)
                    .withTransport(httpsToken)
                    .call()
            }

            val effectiveBranch = SyncBranchNames.reconcileLegacyDefault(branch) { name ->
                repository.exactRef("refs/remotes/$ORIGIN_REMOTE/$name") != null
            }

            val localRef = repository.exactRef("refs/heads/$effectiveBranch")
            if (localRef != null) {
                val trackingRef = "refs/remotes/$ORIGIN_REMOTE/$effectiveBranch"
                if (repository.exactRef(trackingRef) == null) {
                    error("remote branch not found: $effectiveBranch")
                }
                if (repository.branch != effectiveBranch) {
                    git.checkout().setName(effectiveBranch).call()
                }
                return@runCatching effectiveBranch
            }

            val trackingRef = "refs/remotes/$ORIGIN_REMOTE/$effectiveBranch"
            if (repository.exactRef(trackingRef) == null) {
                error("remote branch not found: $effectiveBranch")
            }

            git.checkout()
                .setCreateBranch(true)
                .setName(effectiveBranch)
                .setStartPoint("$ORIGIN_REMOTE/$effectiveBranch")
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call()
            effectiveBranch
        }
    }

    private fun <C : TransportCommand<*, *>> C.withTransport(httpsToken: String?): C {
        val callback = httpsToken?.let {
            HttpsTokenCredentialsProviderFactory.transportConfigCallback(it)
        }
        callback?.let { setTransportConfigCallback(it) }
        return this
    }
}
