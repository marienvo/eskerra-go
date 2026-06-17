package com.eskerra.go.data.git

import java.io.File
import java.net.URI
import org.eclipse.jgit.api.Git

/** Resolves a configured branch name against ls-remote results. */
internal object GitRemoteBranchProbe {

    fun resolveRemoteBranch(
        remoteUri: String,
        branch: String,
        httpsToken: String?
    ): Result<String> = runCatching {
        validateFileRemoteExists(remoteUri)
        val lsRemote = Git.lsRemoteRepository().setRemote(remoteUri)
        httpsToken?.let { token ->
            lsRemote.setTransportConfigCallback(
                HttpsTokenCredentialsProviderFactory.transportConfigCallback(token)
            )
        }
        val remoteBranches = lsRemote.call().mapNotNull { ref ->
            ref.name.removePrefix("refs/heads/").takeIf { it != ref.name }
        }.toSet()
        val effectiveBranch = SyncBranchNames.reconcileLegacyDefault(branch) { name ->
            remoteBranches.contains(name)
        }
        if (remoteBranches.isEmpty()) {
            error("Remote repository not found: $remoteUri")
        }
        if (!remoteBranches.contains(effectiveBranch)) {
            error("remote branch not found: $effectiveBranch")
        }
        effectiveBranch
    }

    private fun validateFileRemoteExists(remoteUri: String) {
        if (!remoteUri.startsWith("file:", ignoreCase = true)) return
        val path = File(URI(remoteUri))
        if (!path.exists()) {
            error("$remoteUri: not found")
        }
    }
}
