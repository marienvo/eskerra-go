# AGENTS.md

Canonical agent instructions for **eskerra-go** (Android/Kotlin/Jetpack Compose).

Shared conventions (language, quality, specs discipline, testing, skills, git guardrails) are synced from the sibling **notebox** repo. Re-sync with:

```bash
/path/to/notebox/scripts/sync-shared-conventions.sh /path/to/eskerra-go
```

Do not edit synced `.cursor/rules/{language,quality,specs,testing}.mdc`, `.cursor/skills/`, `.claude/settings.json`, or `.claude/hooks/` in this repo — change them in **notebox** and re-run the script.

`plan-next-pr` is now **synced from notebox** too (edit it there). Its notebox-only paths/gates are wrapped in `repo-specific` marker blocks that the sync strips, leaving eskerra-go's gradle/G-taxonomy fallbacks — so re-syncs keep it in step with notebox.

The genuinely **repo-local** skills (not in the sync manifest, so they survive re-syncs and must be edited here) are the review skills — `review-markdown-integrity-data-loss-prevention`, `review-state-consistency-coroutine-safety`, `review-architecture-drift-responsibility-boundaries` — and `android-performance-debug-loop`.

## Project-specific rules

Architecture style (hybrid layering + feature slices) and placement rules for new code: [`specs/adr/001-hybrid-layering-and-feature-slices.md`](specs/adr/001-hybrid-layering-and-feature-slices.md).

- UI code must not read files directly.
- UI code must not call Git directly.
- Composables receive state and callbacks only.
- ViewModels depend on repositories or use cases, not Android Context.
- Git operations live only in `data/git`.
- Markdown parsing and wiki-link resolution live outside UI.
- Inbox editability is a domain rule: inbox notes editable, all other notes read-only.
- **Git sync channels** (see [`specs/architecture/sync-hardening-and-recovery.md`](specs/architecture/sync-hardening-and-recovery.md)):
  - Manual vault sync (`ManualSyncNow`): commits all safe local changes, auto-merges on divergence with conflict sidecars, recovers interrupted Git ops before proceeding.
  - Podcast RSS refresh delegates to `ManualSyncNow` via `SyncPodcastChangesViaVaultSync` after RSS writes `General/`.
  - Podcast mark-as-played uses `SyncPodcastChange`: stages changed **General/** podcast paths only, fetch + fast-forward + push; no auto-merge/rebase/reset on divergence.
  - All git mutations share one mutex.
  - No WorkManager/AlarmManager scheduled sync; read-only remote `fetch` for the shell indicator is allowed on foreground.
- Full-text search uses **Android's bundled SQLite FTS5** (`SQLiteOpenHelper`). See [`specs/plans/android-vault-notes-rebuild-plan.md`](specs/plans/android-vault-notes-rebuild-plan.md) (Phase 7) for the index schema, reconcile strategy, and ranker tiers.
- No multi-workspace support.
- **Module size budget — touch it, tidy it.** Any `.kt` file under `app/src/{main,test,androidTest}/java/` that your change touches must end no larger than `max(400, the line count it had at the merge base)`: a file at or under 400 may never cross 400, and a file already over 400 may only shrink or stay equal. Renamed/moved files inherit the old path's merge-base size; brand-new files have a hard 400-line ceiling; untouched files are left alone. Pre-existing large files are pinned in [`scripts/module-budget-baseline.json`](scripts/module-budget-baseline.json) and that cap **only ratchets down** — `update-module-budget-baseline.sh` will never raise a cap or add an entry for a grown file. Pinning a new large file is a deliberate manual edit of the baseline JSON, justified in the commit message. `./scripts/check-module-budgets.sh` enforces this in CI. See [`specs/team-scalability/README.md`](specs/team-scalability/README.md).
- Every feature slice must include at least one unit test for domain/data behavior.

## Key invariants

**Startup performance:** first screen render is the sacred path. Defer vault scans, git
`fetch`, RSS refresh, markdown parsing, and indexing until after the first frame; use
last-known cached state for first paint, then refresh in the background. Nothing expensive
runs before launch is settled. Authoritative detail:
[`specs/architecture/boot-optimization.md`](specs/architecture/boot-optimization.md)
(app gate, note-registry cache, launch-settled conditions).

**Playlist merge (shared vault contract — must match notebox verbatim):** higher
`controlRevision` wins; if tied, higher `updatedAt` wins; if tied, remote wins. R2 is
authoritative when configured; the vault-local playlist is the offline fallback. Both
apps write the same playlist objects, so this ordering may not drift between them.
Implemented in
[`PlaylistMerge.kt`](app/src/main/java/com/eskerra/go/core/playlist/PlaylistMerge.kt)
(`pickNewerPlaylistEntry`), mirroring `packages/eskerra-core/src/playlist.ts` in notebox.

## Proposing new work

When proposing a new dependency, provider, startup initialization, background process,
persistent cache, or file/vault scan, state: (1) why it is needed, (2) whether it is on
the startup path, (3) whether it can be deferred, (4) the performance risk, (5) how it
should be measured. Before adding work to the startup path, first consider: deferring it,
caching the result, doing less work, reducing frequency, limiting the data set, or lazy
loading.

## Change process

Every change declares one change type (G1–G5) per
[`specs/rules/change-safety.md`](specs/rules/change-safety.md) (binding) — the type sets the
required context, tests, reviewer, and whether an agent may drive it. That file also holds
the green/yellow/**red** file-access tiers (sync/vault-write internals, guardrail ratchets,
CI, and hooks are red: propose-only unless the task explicitly targets them with approved
scope) and the delegated work-order + report format. Three repo-local review skills back it
up: `review-markdown-integrity-data-loss-prevention` (any Markdown/vault write path),
`review-state-consistency-coroutine-safety` (ViewModels, `StateFlow`, coroutine races), and
`review-architecture-drift-responsibility-boundaries` (layer seams, god modules, ownership).

## Observability

Sentry conventions (what is actually sent today: SDK init, tags, the one breadcrumb) live
in [`specs/observability/README.md`](specs/observability/README.md). Renaming a telemetry
event, tag, or fingerprint updates that spec **in the same change**.

## Branding / launcher icons

Launcher mipmaps live under `app/src/main/res/mipmap-*` (adaptive foreground PNGs + `mipmap-anydpi-v26` XML). Play Store / web exports are in [`branding/`](branding/) when present.

When the logo changes, replace the mipmap trees and adaptive XML (Android Studio Image Asset or exported mipmaps), then keep these aligned:

- `values/ic_launcher_background.xml` — `#281943`
- `values/colors.xml` (`splash_background`) — `#281943` (system splash + post-splash window background)
- `drawable/ic_splash_logo.xml` — splash-only logo (35% inset on foreground); launcher mipmaps stay full size

Do not reintroduce a `_android/` staging folder; commit assets directly under `app/src/main/res/`.

## Specs

Non-obvious decisions and product boundaries live under [`specs/`](specs/). See also [`.cursor/rules/project-conventions.mdc`](.cursor/rules/project-conventions.mdc).

## Git hooks (no npm/Husky)

Enable the main-branch guard once per clone:

```bash
git config core.hooksPath scripts/githooks
```

## Commands

Gradle requires **Java 17** (CI uses Temurin 17). Use `./scripts/gradle.sh` for local and agent runs — it picks JDK 17 automatically when `JAVA_HOME` is unset. Plain `./gradlew` is fine when JDK 17 is already your default (as in CI).

### Quality gate (before marking work complete)

Run these in order; resolve all failures before finishing. Matches the CI verify job in `.github/workflows/android-ci.yml`.

```bash
# Module size budgets (requires jq)
./scripts/check-module-budgets.sh

# Minimum gate for Kotlin/UI or Gradle script changes
./scripts/gradle.sh :app:ktlintCheck :app:lintDebug

# Full gate when domain, data, or logic changed
./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest
```

After a deliberate module split, refresh caps with `./scripts/update-module-budget-baseline.sh` and commit `scripts/module-budget-baseline.json`.

Do not rely on CI to catch ktlint or Android lint violations.

### Other commands

```bash
./scripts/gradle.sh :app:assembleDebug
./scripts/install-debug.sh   # build, install on device/emulator, and launch
./scripts/gradle.sh :app:connectedDebugAndroidTest
```

Use `assembleDebug` only when building the APK is the goal. `installDebug` (or the script above) requires a connected device or emulator. `connectedDebugAndroidTest` requires a device or emulator and is not part of the standard quality gate.
