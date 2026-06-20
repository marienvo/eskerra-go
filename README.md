# Eskerra Go

Eskerra Go is the native Android companion for Eskerra: a Git-first Markdown
vault app written in Kotlin and Jetpack Compose.

Migration note: Eskerra Go replaces the React Native mobile app that previously
lived in the Eskerra Studio desktop repo. Desktop work continues in that sibling
repo; Android work belongs here.

---

## Status

Native Android app for the Eskerra Markdown vault. Core note workflows and the Episodes (podcasts) tab are implemented.

**Implemented:**

- Compose shell, workspace setup, inbox/reader/wiki navigation, Today Hub
- Inbox note create/edit/save/delete with Git dirty status
- Full-text vault search (SQLite FTS5)
- Manual HTTPS remote sync (inbox-only commits, fast-forward integration)
- Vault settings UI including Cloudflare R2 credentials
- Restart/offline reliability, boot optimization, stale-while-revalidate caches
- Episodes/podcasts: catalog, Media3 playback, mini player, R2 playlist handoff, pure-Kotlin RSS refresh, mark-as-played, and auto git commit+push per podcast operation

Specs and product boundaries live in [`specs/`](specs/). Agent and project rules live in
[`AGENTS.md`](AGENTS.md).

---

## Product scope

- Git-first setup for one workspace.
- Clone from `file://` or sanitized `https://` remotes; manual inbox sync for HTTPS.
- Read, write (inbox only), commit, fetch, fast-forward, and push path.
- Inbox list from markdown files; Today Hub; vault search.
- Podcast episodes: catalog, playback, R2 playlist sync, RSS refresh.
- Non-inbox notes are read-only except podcast checkbox writes under `General/`.
- Floating shell navigation.

**Git sync channels** (see [`specs/architecture/sync-hardening-and-recovery.md`](specs/architecture/sync-hardening-and-recovery.md)):

- Manual sync: **Inbox/** commits only.
- Podcast mark-as-played and RSS refresh: **separate auto-commits** for changed `General/` podcast paths, then fetch + fast-forward + push when possible. Shared git mutex with manual sync.

**Out of scope:**

- Multi-workspace support.
- Automatic merge/rebase/conflict-resolution UI.
- WorkManager/AlarmManager scheduled background sync.
- SSH remotes.
- iOS.

---

## Platform and stack

- Android only.
- Kotlin + Jetpack Compose + Material 3.
- Gradle wrapper project with one Android app module.
- JDK 17 language/toolchain target.
- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`.
- DataStore Preferences for workspace setup persistence.
- JGit `6.10.0.202406032230-r` for Git operations.

JGit is isolated behind `data/git`. Compose screens must not call Git or read
files directly.

---

## Prerequisites

- **JDK 17**. Do not use JDK 21 or 25 for this project; the current Android
  Gradle Plugin setup expects JDK 17.
- Android SDK, usually installed through Android Studio.
- SDK path configured through either `local.properties` or `ANDROID_HOME`.

Create `local.properties` in the project root if needed:

```properties
sdk.dir=/path/to/Android/Sdk
```

Example on Linux with the default Android Studio SDK location:

```properties
sdk.dir=/home/you/Android/Sdk
```

Gradle commands use JDK 17 via `./scripts/gradle.sh` (auto-detects common JDK 17 paths). Alternatively, export `JAVA_HOME` to JDK 17 and use `./gradlew` directly.

---

## Build and run

Build the debug APK:

```bash
./scripts/gradle.sh :app:assembleDebug
```

Build, install on a connected phone or emulator, and launch the app in one
step:

```bash
./scripts/install-debug.sh
```

Or with Gradle only (installs but does not launch):

```bash
./scripts/gradle.sh :app:installDebug
```

Prerequisites for device install: USB debugging enabled on the phone (or an
emulator running), `adb` on your PATH, and exactly one authorized device
connected. You can also install/run from Android Studio.

If the Gradle wrapper JAR is missing in a fresh local checkout, regenerate it
once with Gradle 8.9+ running on JDK 17:

```bash
gradle wrapper --gradle-version 8.11.1
```

---

## Tests

Fast JVM tests:

```bash
./scripts/gradle.sh :app:testDebugUnitTest
```

Device/emulator instrumentation tests:

```bash
./scripts/gradle.sh :app:connectedDebugAndroidTest
```

The instrumentation tests cover Android runtime behavior such as app-private
storage, DataStore, Keystore credential round-trip, and the JGit repository cycle
under `context.filesDir`.

### Step 9 manual verification (real HTTPS remote)

Before pointing the app at a valuable notes repository, run the disposable-repo
checklist on a device or emulator:

[`docs/verification/step-09-disposable-repo-manual-test.md`](docs/verification/step-09-disposable-repo-manual-test.md)

Use remote URLs like `https://github.com/<owner>/<repo>.git` only. Never embed a
token in the URL (forms like `https://<token>@github.com/...` are rejected).

---

## Module layout

Single module, package boundaries:

```text
app/
  MainActivity.kt
  app/          AppRoot.kt, App.kt, AppRoute.kt, AppShell.kt, app gate/setup view models
  ui/theme/     Material 3 theme
  core/model/   NoteId, NoteSummary, Workspace, WorkspaceConfig, WikiLink
  feature/      inbox, add, note, podcasts, dashboard, menu, setup
  data/         notes, workspace, credentials, git
```

Feature screens are stateless. Route-level composables in `app/App.kt` read
state and pass state plus callbacks into screens.

Important boundaries:

- UI code must not read files directly.
- UI code must not call Git directly.
- Composables receive state and callbacks only.
- ViewModels depend on repositories or use cases, not Android `Context`.
- Git operations live only in `data/git`.
- Markdown parsing and wiki-link resolution live outside UI.
- Inbox editability is a domain rule: inbox notes editable, all other notes
  read-only.

---

## Git implementation notes

The Step 2 Git spike proves core operations from app-private Android storage
using pure Java JGit:

- `WorkspaceGitRepository` is the narrow seam.
- `JGitWorkspaceRepository` is the JGit-backed implementation.
- Operations return Kotlin `Result<T>`; no typed Git error hierarchy exists yet.
- `writeFile` rejects absolute paths, blank paths, and `..` segments before
  writing.
- Commits set an explicit `PersonIdent`; Android has no useful global Git
  identity.
- `pullFastForwardOnly` fails cleanly on divergent history; there is no merge or
  conflict-resolution flow.

JVM unit tests exercise sync against local `file://` bare repositories. Real
HTTPS remotes require on-device verification (see manual checklist above).

HTTPS token auth uses an in-memory JGit credential provider. Tokens are stored
in Keystore-backed encrypted app-private files, not in DataStore metadata or
`.git/config`. SSH is not supported. Scheduled background sync is not used;
podcast operations may auto-commit and push on user action (see sync-hardening spec).

---

## Architecture and conventions

Read [`AGENTS.md`](AGENTS.md) before changing behavior. It is the canonical
project instruction file for this repo.

Non-obvious decisions and architecture notes live under
[`specs/`](specs/), especially:

- [`specs/architecture/app-contract.md`](specs/architecture/app-contract.md)
- [`specs/architecture/git-spike-findings.md`](specs/architecture/git-spike-findings.md)
- [`specs/plans/workspace-setup.md`](specs/plans/workspace-setup.md)

Shared agent conventions are synced from the Eskerra Studio sibling repo. Check
sync state with:

```bash
../notebox/scripts/sync-shared-conventions.sh --check "$(pwd)"
```

---

## Desktop companion

Desktop development happens in the Eskerra Studio sibling repo. Use this repo for
the native Android/Kotlin app; use Eskerra Studio for the Tauri desktop app.

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 only**
(**AGPL-3.0-only**). See [`LICENSE`](LICENSE) for the full license text.

In practical terms:

- **Sharing copies (APKs, binaries, etc.):** If you give others a copy of this
  program or a work based on it, AGPL requires that you also give them the
  corresponding source code under the same license and preserve
  license/copyright notices, in the ways described in the license.
- **Network / "as-a-service" use:** AGPL adds a specific rule for modified
  versions you run as a service: if users interact with your modified version
  remotely through a network, you must offer them the corresponding source of
  your version, in line with section 13 of the license.
- **Dependencies:** This repository uses third-party libraries under their own
  licenses. Your AGPL obligations apply to this project's code and how you
  convey or operate modified versions of it as described above.

This is a short summary, not legal advice. For exact terms, read
[`LICENSE`](LICENSE).

---

## Known limitations

- Android only; iOS is not a target.
- One workspace per install.
- Manual inbox sync and podcast auto-sync use fast-forward-only integration; diverged histories require manual Git repair outside the app.
- Podcast tab rebuild in progress (stub UI until feature phases land).
- Manual HTTPS sync should be validated on a disposable private repo before use on a valuable vault (see
  [`docs/verification/step-09-disposable-repo-manual-test.md`](docs/verification/step-09-disposable-repo-manual-test.md)).
- SSH and WorkManager scheduled sync are not implemented.
