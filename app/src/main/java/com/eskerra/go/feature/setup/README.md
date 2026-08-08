# feature/setup

## Purpose

First-run workspace creation: the screen the app gate shows while
`AppGateState` is `NeedsSetup`. The user picks a mode — initialize a new local
workspace or clone a remote — and on success the gate advances to `Ready`.
The slice collects and validates *input only*; creating the repo, storing the
token, and persisting metadata all live in `data/workspace` behind
`WorkspaceSetupCompletion`.

## Key files

- `WorkspaceSetupViewModel.kt` — form state and `submit()`; delegates to
  `WorkspaceSetupCompletion` and maps failures to a message.
- `WorkspaceSetupUiState.kt` — the whole form as one immutable data class.
- `WorkspaceSetupScreen.kt` — stateless Compose form, plus
  `WorkspaceSetupInputOptions` (the password-style token field defaults).

## State owner

`WorkspaceSetupViewModel` is the single source of truth; the screen is
stateless and reports everything through callbacks. `submit()` guards on
`isSubmitting` so a double tap cannot start two setups — the flag is set
before the coroutine launches and cleared on both result branches.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.setup.*"`

`WorkspaceSetupViewModelTest` injects fake `WorkspaceSetupCompletion`s
(`Success`/`Failing`, plus a recording fake), so no Git, network, or real
filesystem is involved. Setup's persistence and rollback behavior is tested in
`data/workspace`, not here.

## What not to touch

- **The credential must never survive a successful submit.** `submit()` clears
  `credential` from the UI state on success, and `onModeChange` clears it when
  leaving `Clone` — so a token typed for a clone is not forwarded by a later
  `InitializeLocal` setup. Both are pinned by tests; keep them.
- **The token field's password styling** (`WorkspaceSetupInputOptions`) is
  asserted by `WorkspaceSetupScreenSecurityTest` — no visual transformation,
  no autocorrect-off, means a token in the keyboard's learned dictionary.
- **The gate must not advance on failure.** `onSuccess` runs only inside the
  `Result` success branch; `DefaultWorkspaceSetupCompletion` rolls the
  workspace directory and saved credential back when metadata saving fails.
  Do not "optimistically" call `markReady` before the result arrives.
- **Error text is not this slice's.** Messages come from
  `WorkspaceSetupError.message()` in `data/workspace`; the ViewModel only
  supplies the generic fallback. Add or reword failures there.
- Validation of the remote URI (scheme allowlist, embedded-credential
  rejection) belongs to `data/workspace`; the button's `enabled` condition is
  a convenience, never the real gate.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.
