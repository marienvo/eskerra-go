# Cold-start performance logbook

Measurement trail for eskerra-go startup work. The durable destination for anything that becomes a
boot invariant is [`specs/architecture/boot-optimization.md`](../architecture/boot-optimization.md);
this file is the evidence.

Metric definitions used throughout:

- **launch-settled** — process start (`Process.getStartElapsedRealtime()`) to the moment
  `onLaunchSettled` dismisses the splash. This is the sacred-path number: the splash deliberately
  holds past first frame ([`AppLaunchSettled.kt`](../../app/src/main/java/com/eskerra/go/app/AppLaunchSettled.kt)),
  so first frame alone understates what the user waits for. Captured with a **temporary** `Log.i`
  in `MainActivity.onLaunchSettled`, applied identically to both builds and reverted afterwards.
- **TotalTime** — `adb shell am start -W` TotalTime, i.e. to first frame (the splash frame).

---

## 2026-08-02 — H01: boot's forced remote fetch sits on the startup path

**Context.** Verification measurement for the always-on-sync work (now retired plan; behavior lives in
[`specs/architecture/sync-hardening-and-recovery.md`](../architecture/sync-hardening-and-recovery.md)),
which moved the boot sync behind launch settlement. The plan asked only that cold start **not
regress**; the measurement found a sizeable improvement instead, so it is recorded as a hypothesis
result rather than a pass/fail gate.

**H01.** The pre-Phase-C boot path ran `refreshShellStatusQuietly(forceRemote = true)` from a
`LaunchedEffect(config)` at composition time. `forceRemote = true` skips the 30 s debounce, so every
cold start performed a **git fetch over the network** while the vault scan and inbox load — the work
launch settlement actually waits on — were still running. Deferring all sync work until after
launch-settled should therefore shorten launch-settled, not merely leave it unchanged.

**Change measured.** Phase C steps 1–3 (commits `992ee11`, `3f22127`, `634a8bb`, `e6fa68b`):

- before: `LaunchedEffect(config) { appSyncViewModel.refreshShellStatusQuietly(forceRemote = true) }`
- after: `LaunchedEffect(launchSettled) { …guard…; withFrameNanos { }; appSyncViewModel.requestAutoSync() }`

**Conditions.** Same device, same build type, same vault, same session, back-to-back.

| | |
|---|---|
| Device | Stellar-M6 (L768), Android 15, physical, USB |
| Build | `:app:installDebug` (**debug build** — absolute numbers are inflated; trust the relative delta) |
| Vault | 1232 `.md` files under `files/workspace` |
| Procedure | `am force-stop` → poll `pidof` until the process is gone → 2 s settle → `logcat -c` → `am start -W` |
| Validity filter | only runs reporting `LaunchState: COLD` **and** a launch-settled marker are kept; anything else is discarded and retried |
| before | `992ee11` (Phase C step 1: wiring only, boot sync not yet moved) |
| after | `e6fa68b` (Phase C complete) |

**Results** (ms):

| Metric | Build | n | median | mean | min | max |
|---|---|---|---|---|---|---|
| launch-settled | BEFORE | 10 | 4224 | 4427 | 4087 | 5397 |
| launch-settled | AFTER (run 1) | 10 | 3408 | 3458 | 3125 | 4121 |
| launch-settled | AFTER (run 2, A-B-A) | 7 | 3323 | 3403 | 3160 | 3857 |
| TotalTime | BEFORE | 10 | 4468 | 4709 | 4312 | 5731 |
| TotalTime | AFTER (run 1) | 10 | 3676 | 3717 | 3470 | 4354 |
| TotalTime | AFTER (run 2, A-B-A) | 7 | 3631 | 3694 | 3493 | 4065 |

**Deltas (median):** launch-settled **4224 → 3388 ms = −836 ms (−19.8 %)**;
TotalTime **4468 → 3631 ms = −837 ms (−18.7 %)**.

**Classification: Significant.**

**Why this is trusted.** The improvement was surprising (the plan only predicted "no regression"), so
the order was checked for drift with an **A-B-A** design: after → before → after. The two AFTER
blocks agree closely (median 3408 / 3323), and the BEFORE block sits ~800 ms above both, so this is
not a warm-up or thermal artifact. The distributions barely overlap (BEFORE min 4087 vs AFTER max
4121 on launch-settled).

**Conclusion.** Moving the boot sync behind launch settlement did not cost startup time — it
**removed ~840 ms of network work from the startup path**. The old forced `fetch` was a real
violation of the startup invariant hiding in plain sight: it was framed as a cheap "status refresh
for the shell indicator", but `forceRemote = true` made it a debounce-skipping network round trip on
every cold start. Phase C both added the boot/foreground sync behavior and fixed that.

**Caveats.**

- Debug build; absolute numbers are not release numbers. The relative delta is the result.
- Network-dependent: the BEFORE build's cost is a remote `fetch`, so its penalty varies with
  connectivity. On a fast network the gap would narrow; offline it could widen (timeouts).
- The device intermittently reported `LaunchState: UNKNOWN (0)` instead of `COLD` for stretches of
  consecutive attempts (worst case 23 discards before 7 good runs). Those attempts are excluded, not
  averaged in. Cause not investigated — it did not correlate with either build.

**Next.** No follow-up hypothesis queued. If startup work resumes, the open question is where the
remaining ~3.4 s goes: the gate's three layers and the note-registry cache path
(`boot-optimization.md` §note-registry cache) are the obvious next seams to instrument, on a
release-ish build.

---

## 2026-08-12 — Phase breakdown of the remaining ~3.3 s (measurement only)

The previous entry left one open question: where the remaining time goes. This entry answers it.
**No behavior changed** — instrumentation was added, the launch was measured, and the numbers below
are the new baseline that later hypotheses get compared against.

**Conditions.** Same physical device and procedure as the 2026-08-02 entry, debug build, vault of
**1161** `.md` files. Driven by `scripts/measure-cold-start.sh 7`, which discards any run not
reporting `LaunchState: COLD` or lacking a launch-settled marker. Raw per-run traces:
`.perf/cold-start-before.log`.

**Result.** launch-settled median **3269 ms** over 7 cold runs (min 3119, max 4962).

| Phase | Median | Min | Max |
|---|---|---|---|
| Process start → `activity.onCreate` | 618 | 614 | 795 |
| DI construction (main thread) | 155 | 154 | 187 |
| First composition → gate start | 270 | 181 | 346 |
| Gate resolve | 114 | 87 | 151 |
| **Registry snapshot read** | **743** | 678 | 1627 |
| First vault scan | 367 | 173 | 2100 |
| Scan end → launch-settled | 608 | 348 | 1109 |
| **Total (launch-settled)** | **3269** | 3119 | 4962 |

**Two findings, one of which contradicts the pre-measurement ranking.**

1. **The cache hit path is the dominant cost, not a cache miss.** `registry.current` — reading and
   decoding `note_registry_snapshot.json` — costs 678–1627 ms on *every* launch. On fingerprint-hit
   runs the incremental scan works exactly as designed (`hadMemoBase=true`, `reRead=0`,
   `memoHits=1161`, 173–367 ms), so the scan is not the problem; deserializing the snapshot is.
   `SnapshotNoteJsonCodec` reads the whole file into one `String`, splits it character by character
   into top-level objects, and allocates substrings per note; `NoteRegistry.fromNotes` then sorts
   the full list again. Run 4 shows the shape clearly: fingerprint hit, snapshot hit, and still
   4309 ms because the snapshot read alone took 1627 ms.

2. **A fingerprint miss invalidates the gate and the registry snapshot together.** Runs 1 and 3
   reported `gate.fingerprintRead hit=false`; both then had `hadMemoBase=false` and re-read all
   1161 files (2100 ms and 1031 ms). Because `GateFingerprintComputer` includes `.git/HEAD` and the
   branch ref, any commit or pull invalidates all four cold-start caches at once.

**Classification: not applicable** — measurement only, no change to compare.

**Also observed, outside the splash path.** After sync, `ManualSyncNow` calls `invalidate()` then
`refresh()`, which consistently produces `hadMemoBase=false` and a full re-read of all 1161 files
(visible around the 8 s mark in every run). It does not block the splash, but it is repeated work
worth removing.

**Next.** Ranked by measured size, not by guess:

- `H04` — make the registry snapshot cheaper to load. It is the largest single cost and it is paid
  on the good path, so it dominates the achievable win.
- `H05` — decouple snapshot *usability* from the git fingerprint. The scanner re-verifies mtime and
  size per file, so a stale snapshot is safe as a memo base even when the fingerprint moved; that
  removes the 2100 ms worst case without weakening freshness for what is displayed.
- `H06` — stop the post-sync `invalidate()` from discarding a usable memo base.

Note that the pre-measurement favourite (the `current()`/`refresh()` mutex race deciding whether the
scan gets a memo base) turned out to be real but secondary: it only bites on fingerprint-miss runs,
which `H05` addresses more directly.
