# Step 3: Workspace Setup

Step 3 adds the first real workspace setup flow on top of the Step 1 UI skeleton
and Step 2 Git spike.

## What was built

- `core/model/WorkspaceConfig.kt` - persisted non-secret workspace metadata
- `data/workspace/WorkspaceStore.kt` + `DataStoreWorkspaceStore.kt` - Preferences
  DataStore persistence
- `data/workspace/WorkspacePaths.kt` - fixed `workspace/` path under `filesDir`
  with validation (no absolute paths, no `..`)
- `data/credentials/CredentialStore.kt` + `EncryptedCredentialStore.kt` (Keystore-backed) — credential seam (not in DataStore)
- `data/workspace/WorkspaceSetupCompletion.kt` - Git setup + metadata/credential persistence as one result
- `data/workspace/AppGateResolver.kt` - pure launch gate logic
- `app/AppGateViewModel.kt` + `app/AppRoot.kt` - stable root gate
- `data/workspace/WorkspaceSetupError.kt` - typed setup errors
- `app/WorkspaceSetupViewModel.kt` + `feature/setup/WorkspaceSetupScreen.kt`
- JVM tests for fake store, paths, gate resolver, setup repository
- Android instrumentation tests for real DataStore and setup on `filesDir`

Setup is **not** a NavHost route. [`AppRoute.kt`](app/src/main/java/com/eskerra/go/app/AppRoute.kt)
is unchanged.

## Setup modes

- **Initialize local** - `git init` in `filesDir/workspace/`; branch comes from Git status
- **Clone** - `file://` bare remote URIs only; user enters branch name
- **Open existing** - relaunch with valid persisted config only (no picker)

HTTPS credentials and Android Keystore are deferred in Step 3.

## Persistence failure rollback

If credential or metadata persistence fails after Git setup succeeds,
`DefaultWorkspaceSetupCompletion` removes the workspace directory (and any saved
credential) so the user can retry setup without clearing app data.

## Boundaries

- One workspace only; path is fixed to `workspace/` under app-private storage
- No secrets in DataStore; Android Keystore deferred
- [`FakeNotes`](app/src/main/java/com/eskerra/go/data/notes/FakeNotes.kt) still
  powers Inbox and Note after setup
- Real note indexing is Step 4

## Verification

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # device/emulator
```

Manual: clear app data → setup screen → init or clone → Inbox with fake notes →
relaunch skips setup.
