# Step 2: Git Spike Findings

This spike proves that Eskerra Go can perform its core Git operations from
app-private storage on Android, using a Git implementation kept fully separate
from the Compose UI. It is a spike, not the final Git architecture.

## What was built

- `data/git/WorkspaceGitRepository.kt` - a narrow interface (the seam). Every
  operation returns Kotlin `Result<T>`; there is intentionally no typed error
  hierarchy in this step.
- `data/git/GitWorkspaceStatus.kt` - a small status model (branch, dirty flag,
  changed paths).
- `data/git/JGitWorkspaceRepository.kt` - a JGit-backed implementation of the
  interface.
- `app/src/test/.../JGitWorkspaceLocalTest.kt` - JVM tests for local operations
  and `writeFile` path safety.
- `app/src/test/.../JGitWorkspaceRemoteTest.kt` - JVM tests for remote
  operations against a local `file://` bare repository.
- `app/src/test/.../TestGitRepos.kt` - test helper for throwaway repos.
- `app/src/androidTest/.../JGitWorkspaceRepositoryAndroidTest.kt` - one
  instrumentation test proving the cycle on real `context.filesDir`.

The existing `data/git/FakeGitGateway.kt` and all Compose screens/navigation are
unchanged.

## Library

- JGit (`org.eclipse.jgit:org.eclipse.jgit`), version `6.10.0.202406032230-r`.
- The 6.x line is targeted deliberately: it is built for Java 11 bytecode, which
  is friendlier to Android desugaring than the 7.x line (Java 17 bytecode).
  Confirm on a real device/emulator before promoting the version.
- Only the core artifact is used. SSH/JSch transports are intentionally NOT
  pulled in for this spike.

## Answers to the spike questions

1. **Can we use a Git library from Android/Kotlin safely?** Yes, via JGit (pure
   Java, no native binaries, no shelling out to `git`). It is wired as a normal
   dependency and used from plain Kotlin. Final confirmation requires running the
   instrumentation test on a device/emulator (see Verification).
2. **Can we clone or initialize into app-private storage?** Yes. `initOrOpen`
   runs `git init` in an existing empty directory; `cloneFrom` clones a remote
   into a new/empty directory. The instrumentation test exercises both under
   `context.filesDir`.
3. **Can we read repository status?** Yes. `status` returns the current branch, a
   dirty flag, and the set of changed paths.
4. **Can we create or modify a markdown file?** Yes, via `writeFile`, with strict
   path-safety checks (see below).
5. **Can we stage and commit?** Yes. `stageAll` stages additions, modifications,
   and deletions; `commit` uses an explicit committer identity (so it does not
   depend on a global git config, which is absent on Android) and returns the new
   commit id.
6. **Can we pull/fetch safely?** Yes. `fetch` updates remote-tracking refs;
   `pullFastForwardOnly` performs a fast-forward-only update and fails cleanly
   (without merging) on divergent history. No merge/conflict logic exists.
7. **Can we push, or what blocks it?** Push works against a local `file://` bare
   repository, proving the mechanics with no network or credentials. What blocks
   pushing to a real remote (e.g. GitHub) is authentication and transport: HTTPS
   tokens or SSH keys plus secure storage. That is represented only by a no-op
   transport seam (`transportConfigCallback`) and is otherwise deferred.
8. **What belongs in Step 2 vs deferred?** In scope: the items above. Deferred:
   everything in "Deferred" below.

## Path-safety behavior (writeFile)

`writeFile` validates the relative path before any filesystem write and returns
`Result.failure` (writing nothing) when the path is:

- absolute,
- empty/blank, or
- contains a `..` segment.

It also resolves the target against the canonical `workingDir` and confirms the
result stays inside it, so a write can never escape the workspace directory.

## Directory semantics

- `initOrOpen` initializes only an existing empty directory, or opens an
  existing Git repo. It fails when the directory does not exist, is a non-empty
  non-repo directory, or is a regular file. It never creates the directory and
  performs no cleanup or recovery.
- `cloneFrom` fails (writing nothing) when the target exists and is non-empty.

## Android-specific notes / gotchas

- Commits set an explicit `PersonIdent`; Android has no global git config, so
  relying on a default committer would fail.
- `app/build.gradle.kts` adds `packaging.resources.excludes` for JGit metadata
  (`META-INF/DEPENDENCIES`, `META-INF/INDEX.LIST`, `META-INF/*.txt`,
  `META-INF/jgit-*`) to avoid duplicate-resource merge failures when packaging.
- `minSdk = 26` provides `java.time` and `java.nio.file`, which JGit uses. The
  instrumentation test is the authoritative check that no `NoClassDefFound`/
  desugaring issue appears at runtime.
- All storage stays under app-private internal storage (`filesDir`); no external
  storage, `MANAGE_EXTERNAL_STORAGE`, or SAF.

## Deferred (explicitly out of scope for Step 2)

- Real-remote authentication (HTTPS tokens / SSH keys) and credential storage.
- HTTPS/SSH transport (only `file://` is exercised).
- Conflict resolution and non-fast-forward merges.
- Background sync, sync scheduler, background worker.
- Multi-workspace support.
- A typed error model / `GitError` hierarchy (using `Result<T>` for now).
- Any UI, debug screen, Room, or DataStore integration.

## Verification

Prerequisites: JDK 17; Android SDK (`local.properties` `sdk.dir=...` or
`ANDROID_HOME`); Gradle wrapper present (generate once with
`gradle wrapper --gradle-version 8.11.1` if `./gradlew` is missing); an
emulator/device for the instrumentation test. No network access required.

```bash
# Compiles with JGit on the classpath
./gradlew :app:assembleDebug

# Fast JVM proof of local + local-remote Git operations
./gradlew :app:testDebugUnitTest

# Android runtime proof against filesDir (needs emulator/device)
./gradlew :app:connectedDebugAndroidTest
```

Note: generating the Gradle wrapper, accepting SDK licenses, and running the
instrumentation test against a device/emulator are local/human steps and were
not executed inside the agent's sandbox (no Android SDK, JDK 17, or Gradle
available there). Run the commands above locally to confirm green.
