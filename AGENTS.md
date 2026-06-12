# AGENTS.md

Canonical agent instructions for **eskerra-go** (Android/Kotlin/Jetpack Compose PoC).

Shared conventions (language, quality, specs discipline, testing, skills, git guardrails) are synced from the sibling **notebox** repo. Re-sync with:

```bash
/path/to/notebox/scripts/sync-shared-conventions.sh /path/to/eskerra-go
```

Do not edit synced `.cursor/rules/{language,quality,specs,testing}.mdc`, `.cursor/skills/`, `.claude/settings.json`, or `.claude/hooks/` in this repo — change them in **notebox** and re-run the script.

## Project-specific rules

Architecture style (hybrid layering + feature slices) and placement rules for new code: [`specs/adr/001-hybrid-layering-and-feature-slices.md`](specs/adr/001-hybrid-layering-and-feature-slices.md).

- UI code must not read files directly.
- UI code must not call Git directly.
- Composables receive state and callbacks only.
- ViewModels depend on repositories or use cases, not Android Context.
- Git operations live only in `data/git`.
- Markdown parsing and wiki-link resolution live outside UI.
- Inbox editability is a domain rule: inbox notes editable, all other notes read-only.
- No background sync in the PoC (no automatic commit/push/pull; read-only remote `fetch` for the shell indicator is allowed — see [`specs/architecture/sync-hardening-and-recovery.md`](specs/architecture/sync-hardening-and-recovery.md)).
- No full-text search in the PoC.
- No multi-workspace support in the PoC.
- Module budgets enforce file size in CI. New `.kt` files may not exceed **400** lines without a baseline entry; files **≥800** lines may not grow without an intentional baseline bump. See [`specs/team-scalability/README.md`](specs/team-scalability/README.md) and [`scripts/module-budget-baseline.json`](scripts/module-budget-baseline.json).
- Every feature slice must include at least one unit test for domain/data behavior.

## Branding / launcher icons

Launcher mipmaps live under `app/src/main/res/mipmap-*` (adaptive foreground PNGs + `mipmap-anydpi-v26` XML). Play Store / web exports are in [`branding/`](branding/) when present.

When the logo changes, replace the mipmap trees and adaptive XML (Android Studio Image Asset or exported mipmaps), then keep these aligned:

- `values/ic_launcher_background.xml` — `#281943`
- `values/colors.xml` (`splash_background`) — `#281943` (system splash + post-splash window background)
- `drawable/ic_splash_logo.xml` — splash-only logo (35% inset on foreground); launcher mipmaps stay full size

Do not reintroduce a `_android/` staging folder; commit assets directly under `app/src/main/res/`.

## Specs

Non-obvious decisions and PoC scope live under [`specs/`](specs/). See also [`.cursor/rules/project-conventions.mdc`](.cursor/rules/project-conventions.mdc).

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
