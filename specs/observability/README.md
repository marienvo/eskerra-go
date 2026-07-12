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

- **Automatic crash/ANR capture** — the only source of events today. No custom
  `Sentry.captureMessage` / `captureException` / `setFingerprint` calls exist anywhere in
  the app.
- **One breadcrumb**: `"app.start"`, added after a successful SDK init. Breadcrumbs attach
  to whatever event fires next in the session; this one exists to confirm in a crash
  report that Sentry itself came up cleanly.

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

No custom events, no user feedback capture, no session-replay, no performance
transactions. If any of these are added, this file gets a new section in that change —
not before.
