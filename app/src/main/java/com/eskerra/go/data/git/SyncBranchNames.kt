package com.eskerra.go.data.git

/** Heuristic branch reconciliation for legacy local workspaces. */
object SyncBranchNames {

    const val LEGACY_DEFAULT = "master"
    const val MODERN_DEFAULT = "main"

    /**
     * When [configuredBranch] is the legacy default and only [MODERN_DEFAULT] exists on
     * the remote, return [MODERN_DEFAULT]; otherwise return [configuredBranch].
     */
    fun reconcileLegacyDefault(
        configuredBranch: String,
        hasRemoteTrackingBranch: (String) -> Boolean
    ): String {
        if (configuredBranch != LEGACY_DEFAULT) {
            return configuredBranch
        }
        if (hasRemoteTrackingBranch(LEGACY_DEFAULT)) {
            return configuredBranch
        }
        if (hasRemoteTrackingBranch(MODERN_DEFAULT)) {
            return MODERN_DEFAULT
        }
        return configuredBranch
    }
}
