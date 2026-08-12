# Observability — Sentry conventions

What this app actually sends today. Not aspirational — extend this file when the code
changes, not before.

## SDK init

`EskerraGoApplication.onCreate()` initializes Sentry manually (`SentryAndroid.init`);
auto-init is disabled via the `io.sentry.auto-init` manifest meta-data. Init is guarded:
an empty `SENTRY_DSN` build config field skips init entirely (e.g. local dev without a
configured DSN), and the whole init call is wrapped in `runCatching` — a failure logs a
`Log.w` and disables error reporting for that process rather than crashing startup.

## Configuration

- `environment`: `"development"` when `BuildConfig.DEBUG`, else `"production"`.
- `release`: `"<applicationId>@<versionName>+<versionCode>"`.
- `isSendDefaultPii = false` — no automatic PII (device contacts, user identifiers, etc).
- `tracesSampleRate = 0.0`, `profilesSampleRate = 0.0` — performance tracing and profiling
  are off; only error/crash reporting is active.
- Tag `app = "eskerra-go"` on every event, set once at init (distinguishes this app's
  events from other projects sharing the same Sentry org).

## Events

- **Automatic crash/ANR capture** — level `error`, no custom fingerprint.
- **One custom event**: `perf.cold_start.weekly` (see below), level `info`.
- **One breadcrumb**: `"app.start"`, added after a successful SDK init. Breadcrumbs attach
  to whatever event fires next in the session; this one exists to confirm in a crash
  report that Sentry itself came up cleanly.

`data/observability/SentryPerformanceReporter` is the only place outside
`EskerraGoApplication` that touches the Sentry SDK. Everything else reports through the
`PerformanceReporter` port in `core/repository`. Enforced by the
`sentryIsAccessedOnlyFromObservability` rule in `ArchitectureLayerRulesTest`.

## Custom event: `perf.cold_start.weekly`

The one place this app sends something that is not a crash. Cold-start performance is a
core-experience metric, so the app aggregates it locally and reports a summary at most
once a week.

| Property | Value |
| --- | --- |
| Message | `perf.cold_start.weekly` |
| Level | `info` — the only non-error event the app sends |
| Fingerprint | `perf-cold-start-weekly`, fixed |
| Tag | `perf.report = "cold_start_weekly"` |
| Context | `cold_start` (fields below) |

The fingerprint is deliberately fixed so every weekly report groups into a single issue
and the trend across weeks reads as one timeline rather than a new issue each week.

**When it fires.** There is no scheduler in this app (no WorkManager or AlarmManager, by
design), so "weekly" is a check on launch: the first launch after 7 days have passed since
the last report carries it, and only when the window holds at least 5 samples. It runs
from `AppBootEffects` behind the same `launchSettled` + one-frame gate as the boot sync —
telemetry about the launch must never become part of the launch.

**Payload.** Durations in milliseconds, counts, and rates in `[0.0, 1.0]`. Each phase is
reported as `<phase>_median_ms` and `<phase>_p90_ms`; median and p90 rather than a mean,
because launch times are skewed and a mean hides the bad launches.

- Phases: `total`, `process_to_activity`, `di_build`, `to_gate_start`, `gate_resolve`,
  `snapshot_read`, `first_scan`, `settle_tail`
- `sample_count`, `window_days`, `median_note_count`
- `fingerprint_hit_rate`, `snapshot_hit_rate`, `memo_base_rate` — how often the workspace
  fingerprint still matched, the registry snapshot was usable, and the scan had a memo base

**No vault content.** The payload is durations and counts only: no note titles, paths,
remote URIs, or file contents can reach it, because `ColdStartSample` has nowhere to put
them. `DataStoreColdStartStatsStore.NON_SECRET_PREFERENCE_KEY_NAMES` records every key the
local window writes, the same audit pattern `DataStoreWorkspaceStore` uses.

Nothing is sent when the SDK never initialised (empty `SENTRY_DSN`, the normal local
case), and the whole send is wrapped in `runCatching`.

## Build-time (Gradle plugin)

`sentry { ... }` in `app/build.gradle.kts`: org `personal-133`, project `eskerra-go`.
ProGuard mapping upload (`includeProguardMapping` / `autoUploadProguardMapping`) is gated
on `SENTRY_AUTH_TOKEN` being present and runs for release builds only
(`ignoredBuildTypes = ["debug"]`). `tracingInstrumentation` is explicitly disabled,
matching the `tracesSampleRate = 0.0` runtime setting above.

## Binding rule

Renaming a telemetry event, tag, or fingerprint, or changing what a breadcrumb/tag means,
updates this file **in the same change** (see `AGENTS.md`).

## Out of scope today

No user feedback capture, no session-replay, and no performance *transactions* — the
weekly report above is a plain event, so it needs neither `tracesSampleRate` nor the
Gradle plugin's tracing instrumentation, both of which stay off. If any of these are
added, this file gets a new section in that change — not before.
