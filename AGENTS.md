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

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
