# AGENTS.md

Canonical agent instructions for **eskerra-go** (Android/Kotlin/Jetpack Compose PoC).

Shared conventions (language, quality, specs discipline, testing, skills, git guardrails) are synced from the sibling **notebox** repo. Re-sync with:

```bash
/path/to/notebox/scripts/sync-shared-conventions.sh /path/to/eskerra-go
```

Do not edit synced `.cursor/rules/{language,quality,specs,testing}.mdc`, `.cursor/skills/`, `.claude/settings.json`, or `.claude/hooks/` in this repo — change them in **notebox** and re-run the script.

## Project-specific rules

- UI code must not read files directly.
- UI code must not call Git directly.
- Composables receive state and callbacks only.
- ViewModels depend on repositories or use cases, not Android Context.
- Git operations live only in `data/git`.
- Markdown parsing and wiki-link resolution live outside UI.
- Inbox editability is a domain rule: inbox notes editable, all other notes read-only.
- No background sync in the PoC.
- No full-text search in the PoC.
- No multi-workspace support in the PoC.
- Prefer small files. New files over 250 lines need justification.
- Every feature slice must include at least one unit test for domain/data behavior.

## Specs

Non-obvious decisions and PoC scope live under [`specs/`](specs/). See also [`.cursor/rules/project-conventions.mdc`](.cursor/rules/project-conventions.mdc).

## Git hooks (no npm/Husky)

Enable the main-branch guard once per clone:

```bash
git config core.hooksPath scripts/githooks
```

## Commands

Gradle requires **Java 17** (CI uses Temurin 17). If the default JDK is newer, set `JAVA_HOME` first — for example `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk`.

### Quality gate (before marking work complete)

Run these in order; resolve all failures before finishing. Matches the CI verify job in `.github/workflows/android-ci.yml`.

```bash
# Minimum gate for Kotlin/UI or Gradle script changes
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./gradlew :app:ktlintCheck :app:lintDebug

# Full gate when domain, data, or logic changed
JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest
```

Do not rely on CI to catch ktlint or Android lint violations.

### Other commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Use `assembleDebug` only when building the APK is the goal. `connectedDebugAndroidTest` requires a device or emulator and is not part of the standard quality gate.
