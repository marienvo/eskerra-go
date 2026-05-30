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
    ): Result<Unit> = runCatching {
        Git.open(workingDir).use { git ->
            val repository = git.repository
            val localRef = repository.exactRef("refs/heads/$branch")
            if (localRef != null) {
                if (repository.branch != branch) {
                    git.checkout().setName(branch).call()
                }
                return@runCatching
            }

            if (fetchIfNeeded) {
                git.fetch()
                    .setRemote(ORIGIN_REMOTE)
                    .withTransport(httpsToken)
                    .call()
            }

            val trackingRef = "refs/remotes/$ORIGIN_REMOTE/$branch"
            if (repository.exactRef(trackingRef) == null) {
                error("remote branch not found: $branch")
            }

            git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .setStartPoint("$ORIGIN_REMOTE/$branch")
                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call()
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
