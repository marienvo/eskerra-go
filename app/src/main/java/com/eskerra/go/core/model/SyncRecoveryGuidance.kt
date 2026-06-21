package com.eskerra.go.core.model

/** Maps typed sync errors to safe recovery hints for the UI. */
object SyncRecoveryGuidance {

    fun forError(error: SyncError): SyncRecoveryAction = when (error) {
        SyncError.MissingRemoteConfig -> SyncRecoveryAction(
            hint = "Open Remote sync settings and add a repository URL.",
            suggestOpenSettings = true
        )
        SyncError.MissingCredential -> SyncRecoveryAction(
            hint = "Add or replace the access token in sync settings.",
            suggestOpenSettings = true
        )
        SyncError.InvalidRemoteUri -> SyncRecoveryAction(
            hint = "Remove any username or password from the remote URL in settings.",
            suggestOpenSettings = true
        )
        SyncError.UnsupportedRemoteScheme -> SyncRecoveryAction(
            hint = "Use an https:// remote URL in sync settings.",
            suggestOpenSettings = true
        )
        SyncError.InvalidBranch -> SyncRecoveryAction(
            hint = "Enter a valid branch name in sync settings.",
            suggestOpenSettings = true
        )
        SyncError.WorkspaceUnavailable -> SyncRecoveryAction(
            hint = "The workspace Git data is missing. Restore from backup or set up again.",
            localNotesAvailable = false
        )
        SyncError.NonInboxLocalChanges -> SyncRecoveryAction(
            hint = "Unexpected local changes outside Inbox/ were detected. " +
                "Vault sync normally commits these; if sync is blocked, inspect the repo with Git on a computer."
        )
        SyncError.NonInboxStagedChanges -> SyncRecoveryAction(
            hint = "Non-Inbox/ files are staged in Git. Vault sync normally handles this; " +
                "if sync is blocked, unstage or commit them with Git on a computer."
        )
        SyncError.UnexpectedStagedChanges -> SyncRecoveryAction(
            hint = "Unstage unexpected files with Git on a computer before podcast sync runs."
        )
        SyncError.UnsafeLocalPath -> SyncRecoveryAction(
            hint = "Fix unsafe working tree paths with Git on a computer before syncing."
        )
        SyncError.AuthenticationFailed -> SyncRecoveryAction(
            hint = "The token may be expired or revoked. Update it in sync settings and try again.",
            suggestOpenSettings = true
        )
        SyncError.RemoteUnavailable -> SyncRecoveryAction(
            hint = "Check your network connection and try again."
        )
        is SyncError.RemoteBranchNotFound -> SyncRecoveryAction(
            hint = "Confirm the branch exists on the remote or update sync settings.",
            suggestOpenSettings = true
        )
        is SyncError.LocalBranchNotFound -> SyncRecoveryAction(
            hint = "Update sync settings or fetch the branch on a computer.",
            suggestOpenSettings = true
        )
        SyncError.Diverged -> SyncRecoveryAction(
            hint = "Local and remote histories diverged. Tap Sync to integrate automatically, " +
                "or reconcile with Git on a computer if you prefer manual control."
        )
        SyncError.ConflictRisk -> SyncRecoveryAction(
            hint = "Local and remote changes conflict. Reconcile with Git on a computer; " +
                "do not reset from the app."
        )
        SyncError.PushRejected -> SyncRecoveryAction(
            hint = "The remote rejected the push. Fetch and reconcile on a computer; " +
                "local commits were kept."
        )
        SyncError.ManualInterventionRequired -> SyncRecoveryAction(
            hint = "Git was interrupted. Tap Sync to recover automatically, " +
                "or finish or abort the operation on a computer " +
                "before podcast mark-as-played."
        )
        SyncError.SyncAlreadyRunning -> SyncRecoveryAction(
            hint = "Wait for the current sync to finish."
        )
        SyncError.RegistryRefreshFailed -> SyncRecoveryAction(
            hint = "Try Refresh status or reopen Inbox to reload the note list."
        )
        is SyncError.GitFailed -> SyncRecoveryAction(
            hint = "Try again. If the problem continues, inspect the repository " +
                "with Git on a computer."
        )
    }
}
