---
name: android-performance-debug-loop
description: >-
  Guides systematic performance debugging for the eskerra-go Android app: analyze
  cold-start/boot timings and traces, rank hypotheses, test one isolated change at
  a time, compare before/after measurements, and persist conclusions under
  specs/performance. Use when debugging slow cold start, boot/launch latency,
  Compose jank or recomposition, note-switching lag, or when the user asks for a
  measured Android performance investigation.
---

<!-- REPO-LOCAL skill (eskerra-go) — not part of the notebox sync set; sync-shared-conventions.sh leaves this directory alone. Edit it here. -->

# Android performance debug loop

Use this workflow for **Android / Jetpack Compose** performance work in eskerra-go. Startup is
**the sacred path** ([AGENTS.md](../../../AGENTS.md); [specs/architecture/boot-optimization.md](../../../specs/architecture/boot-optimization.md)):
cold start keeps the splash until launch is *settled*, so every millisecond before that gate
resolves is felt. Logbook entries are part of [specs/](../../../specs/) discipline — document what
you measured, not just the code you changed.

Follow the loop in order; do not skip the logbook step before starting the next hypothesis.

## Loop (strict)

1. **Analyze** current measurements (existing traces, logcat timings, prior logbook entries, the
   exact user steps that feel slow).
2. **Propose** at most **3 hypotheses**, ranked by **likelihood** and **impact**. Use ids `H01`,
   `H02`, `H03`.
3. **Select** exactly **one** hypothesis to test next.
4. **Implement** a **minimal** change that targets **only** that hypothesis.
5. **Measure** with clear **before** and **after** timings — same device/emulator, same build
   type, same workspace/vault size, same scenario. State those conditions with the numbers.
6. **Persist** results under `specs/performance/` **before** moving on:
   - Append to an existing logbook in that folder, or create one if none fits (the dir may not
     exist yet — create it).
   - Record: hypothesis id (`Hxx`); short description; change made (file-level if helpful);
     before / after timings (raw numbers, N runs); classification — one of **Significant**,
     **Limited**, **No significant difference**, **Pending**; a clear **conclusion** (what was
     learned, what to try or avoid next).

## What to measure, and how

Prefer cheap, repeatable measurements over one-off profiler sessions.

- **Cold-start / launch latency** — the primary metric. `adb shell am start -W -n com.eskerra.go/.MainActivity`
  (TotalTime / WaitTime), run several times after `adb shell am force-stop com.eskerra.go`. Cross-check
  against **launch-settled** ([AppLaunchSettled.kt](../../../app/src/main/java/com/eskerra/go/app/AppLaunchSettled.kt)),
  not just first-frame — the splash holds past first frame by design.
- **Where the boot time goes** — the app gate's three layers (DataStore read → local gate on
  `Dispatchers.IO` → optimistic fingerprint) and the note-registry cache (in-memory → persisted
  snapshot → incremental scan). Add temporary timing logs around these seams; confirm the
  optimistic-fingerprint fast path is actually hit (warm start) vs. forced full scans (cold miss).
- **Compose jank / recomposition** — Perfetto/`systrace` for frame timing; composition tracing or
  Layout Inspector recomposition counts for unstable/over-recomposing composables. Reproduce the
  janky interaction (scroll, note switch, link tap) deterministically.
- **Traces** — capture a Perfetto/system trace for anything you can't explain from timing logs;
  attach or summarize it in the logbook entry.
- **Optional (not present yet):** a `androidx.benchmark` macrobenchmark module gives repeatable
  `StartupTimingMetric` numbers. Propose adding it only if hand timings prove too noisy — it is a
  new module, so treat that as its own change, not part of a measurement step.

Build/run with the repo commands: `./scripts/gradle.sh :app:assembleDebug`,
`./scripts/install-debug.sh` (build, install, launch). Keep the quality gate green
(`./scripts/gradle.sh :app:ktlintCheck :app:lintDebug`, and `:app:testDebugUnitTest` when logic
changed) for any change you keep.

## Rules

- **One hypothesis per step** — do not combine multiple optimizations or unrelated fixes.
- **No unrelated refactors** — keep diffs scoped to the measurement. Respect the module-size budget
  (`./scripts/check-module-budgets.sh`) and layer boundaries (UI never reads files or calls Git).
- **Preserve measurement clarity** — same procedure; note conditions (device/emulator, debug vs
  release-ish, vault size, warm vs cold).
- **Isolate variables** — narrow experiments over broad "cleanup" passes.
- **Beware the debug build** — debuggable builds are slower and skew absolute numbers; compare
  like-for-like and note the build type. Trust *relative* before/after deltas over absolutes.
- **Write the logbook entry before** proposing or testing the next hypothesis.
- **Do not re-propose** a hypothesis already marked **No significant difference**, unless the
  architecture or the relevant code path materially changed (then state why a retry is fair).

## Logbook conventions

- Prefer a dedicated markdown file per investigation thread (e.g.
  `specs/performance/cold-start-debug-logbook.md`) or append to the file the user already started.
- Use a consistent dated heading per hypothesis so results stay scannable.
- When a conclusion changes a durable boot invariant, fold it into
  [specs/architecture/boot-optimization.md](../../../specs/architecture/boot-optimization.md) —
  the logbook is the trail, that spec is the destination.
