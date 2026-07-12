# SPEC-parity guardrails — seed quality standards from notebox

Status: **active**. Agreed 2026-07-12 (analysis + Q&A in-session). One sequenced
implementation plan in five commits, executed in the order below; each commit leaves
`main` shippable and is reviewable as an individual diff in ~30 minutes.

## Authority and provenance (binding for this whole plan)

Every document, rule, and skill this plan introduces is **seeded** from the notebox repo
and becomes **canonical in eskerra-go the moment it lands here**.

- Seed source: `notebox` @ commit `c72b677a6334560c12441631189dec576ad771e4` (2026-07-12).
  Each seeded file carries a one-line provenance note naming its notebox source path and
  that commit.
- After seeding, **notebox is provenance only** — not an ongoing authority source. If the
  notebox original evolves, that has no effect here; if the eskerra-go copy evolves, do
  not "correct" it back toward notebox.
- **None of this material joins the sync-set.** `scripts/sync-shared-conventions.sh` (the
  existing notebox→eskerra-go mechanism for `.cursor/rules/{language,quality,specs,testing}.mdc`,
  the named `.cursor/skills/`, `.claude/settings.json`, hooks) keeps working exactly as
  today for the files it already owns; nothing from this plan is added to its manifest.
- Drift between the two repos' copies is accepted by design. If it ever hurts, the remedy
  is a manual drift-check skill — a possible later addition, not part of this plan.

New repo-local skills get the same "repo-local" marker `plan-next-pr` already uses, so a
future sync run cannot clobber them (the sync script only replaces skills it names).

---

## Commit 1 — module-budget ratchet fix + touch-it-tidy-it — DONE

Landed. Durable homes: `scripts/lib/module-budget-common.sh` (touch-it-tidy-it +
rename map), `scripts/lib/module-budget-update-lib.sh` (one-way ratchet),
`scripts/check-module-budget-baseline.test.sh` (tests), `AGENTS.md` §
Module size budget, `specs/team-scalability/README.md`,
`.cursor/rules/project-conventions.mdc`.

## Commit 2 — doc map + AGENTS.md invariants — DONE

Landed. Durable homes: `specs/README.md` (doc map + authority order + lifecycle rule),
`AGENTS.md` § Key invariants (startup sacred path + playlist merge) and § Proposing new
work. `specs/observability/` deferred to commit 5.

## Commit 3 — ArchUnit layer rules — DONE

Landed. Durable homes: `app/src/test/java/com/eskerra/go/architecture/ArchitectureLayerRulesTest.kt`,
`app/src/test/resources/archunit.properties` + committed `app/archunit_store/` (frozen-rule
ratchet, may only shrink), `archunit-junit4` in `gradle/libs.versions.toml` +
`app/build.gradle.kts`. Runs inside `:app:testDebugUnitTest`.

Landed rule set (four active, one frozen):

1. `jgitIsAccessedOnlyFromDataGit` — `org.eclipse.jgit..` accessed only from `..data.git..`. Active, clean.
2. `uiDoesNotDependOnDataGit` — `..feature..`/`..ui..` do not depend on `..data.git..`. Active, clean.
3. `viewModelsDoNotDependOnAndroidContext` — classes assignable to `androidx.lifecycle.ViewModel` do not depend on `android.content.Context`. Active, clean.
4. `coreStaysBelowUiAndApp` — `..core..` does not depend on `..feature..`/`..ui..`/`..app..`. Active, clean. (Landed instead of a narrower markdown/wiki-link-only rule: the actual packages are `core.markdown` and `core.wikilink`, both already under `..core..`, so the general inward-dependency rule subsumes the planned one.)
5. `uiDoesNotTouchFileApis` — `..feature..`/`..ui..` do not depend on `java.nio.file..` or `java.io.File`. **Frozen** (`FreezingArchRule`, 68 pre-existing violations, mostly image loading) — the store may only shrink.

Deviation from the original plan: `ImportOption.DoNotIncludeTests` does not exclude
Android's unit-test/instrumentation-test compiled output directories, so rules 1 and 4
initially flagged test code (e.g. tests driving JGit directly). Added a custom
`ExcludeAndroidTestClasses : ImportOption` (excludes paths containing `UnitTest`,
`AndroidTest`, `/test/`) instead.

Not landed, by design: no generic "composables receive state and callbacks only" rule —
ArchUnit has no reliable detectable category for composables absent a dedicated package
or naming convention. Stays prose (and review-skill territory, commit 4).

## Commit 4 — change-safety rules + review skills — DONE

Landed. Durable homes: `specs/rules/change-safety.md` (G1–G5 taxonomy, green/yellow/red
file tiers grounded on real `data/git` / write-path / FTS files, work-order + report
format, standing rules), the two repo-local review skills under `.cursor/skills/`
(`review-markdown-integrity-data-loss-prevention`, seeded + platform-neutral;
`review-state-consistency-coroutine-safety`, fresh for Kotlin/Compose), and `AGENTS.md`
§ Change process. Both new skills carry the repo-local marker and are ignored by
`sync-shared-conventions.sh --check` (verified).

## Commit 5 — minimal observability spec

1. **`specs/observability/README.md`**: the Sentry event/tag naming conventions this app
   actually uses today (inventory the existing `captureMessage`/tag call sites first;
   document what exists, don't design aspirationally).
2. One binding rule, in that file and as a line in `AGENTS.md`: renaming a telemetry
   event or changing its tags/fingerprint updates the observability spec **in the same
   change**.
3. Add the now-existing `specs/observability/` folder to the doc map in `specs/README.md`
   (deferred from commit 2).

---

## Housekeeping note (separate from this plan's slices)

The synced skill `.cursor/skills/ubiquitous-language/` is stale
(`sync-shared-conventions.sh --check` fails on it). Fix by running the existing sync
script from notebox — **this stays under the existing sync mechanism** and must not be
mixed into any of the commits above, precisely because those introduce locally-canonical
material and this file remains notebox-owned. One trivial standalone commit, its message
naming the sync script.

## Lifecycle

Per `specs/plans/README.md`: each implementation commit deletes or shrinks its completed
slice from this plan. Durable outcomes land in their homes — `specs/rules/`,
`specs/README.md`, `AGENTS.md`, `specs/observability/`, the scripts and tests — and the
final implementation commit deletes this file.
