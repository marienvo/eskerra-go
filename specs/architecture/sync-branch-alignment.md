# Sync branch alignment

## Rule

`WorkspaceConfig.branch` is the configured sync branch. Manual sync, sync status comparison, and push/pull all use that name—not the incidental default branch created by `git init`.

The local repository must have `refs/heads/<configured-branch>` and should have `HEAD` checked out on that branch before:

- comparing with `origin/<configured-branch>`,
- committing inbox changes during manual sync, or
- saving remote sync settings that change the configured branch.

## How alignment happens

1. **Initialize local (PoC):** `git init` uses initial branch `main` so a fresh workspace matches the setup UI default and common hosted remotes.
2. **Save remote sync settings:** After `origin` is configured, the app fetches (when needed) and runs `ensureLocalBranch` so the local checkout matches the saved branch (checkout existing branch or create a tracking branch from `origin/<branch>`).
3. **Manual sync:** After local change guards pass and before inbox commit, the app calls `ensureLocalBranch` again so a stale checkout cannot leave sync on the wrong branch.
4. **Legacy `master` metadata:** When persisted branch is `master` but `origin/master` is missing and `origin/main` exists (common on GitHub/GitLab), sync and connection test treat the effective branch as `main`, update `WorkspaceConfig`, and check out `main` on app start and before manual sync.
5. **Clone from remote:** Before clone, ls-remote resolves the branch (including `master` → `main`). HTTPS clone requires `INTERNET` in `AndroidManifest.xml`.

## Errors

- Missing local branch after alignment attempt → `SyncError.LocalBranchNotFound` (stable message, no raw JGit text).
- Missing `origin/<branch>` after fetch → `SyncError.RemoteBranchNotFound` (same as sync compare).
- **Test connection** remains ls-remote only; it does not align the local checkout.

## UI

The sync screen shows the configured branch from `WorkspaceConfig`. When status reads a different checked-out branch, the screen shows a short mismatch hint so users are not misled by diverging labels.

## Out of scope

Renaming or deleting obsolete local branches (for example leaving `master` after switching to `main`), automatic branch guessing, and multi-branch workflows.
