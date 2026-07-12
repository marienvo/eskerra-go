---
name: review-state-consistency-coroutine-safety
description: >-
  Reviews Kotlin coroutine and Jetpack Compose code for StateFlow/snapshot-state
  drift, viewModelScope races, stale captures in composables, unsafe state
  updates, and multiple owners of the same state. Use when debugging intermittent
  UI or persistence bugs, reviewing ViewModels, collectors, or Compose state, or
  when the user mentions stale state, race conditions, or two sources of truth.
---

<!-- REPO-LOCAL skill (eskerra-go) — written fresh for Kotlin/Compose; the notebox counterpart is React-specific. Not part of the notebox sync set; sync-shared-conventions.sh leaves this directory alone. -->

# State Consistency & Coroutine Safety

This skill prioritizes real-world correctness over theoretical rules. Only flag issues that
can realistically break behavior over time. Prefer asking **"can this actually go wrong?"**
over reflex lint-style alerts.

## Why it exists

Eskerra Go holds complex state across `StateFlow`s, Compose snapshot state, and suspending
work in `viewModelScope` (sync, note loading, playlist polling, workspace gate). Bugs here
are intermittent and expensive: stale UI, lost updates, or a write that lands against the
wrong note after a fast switch. Several of these surfaces are red tier in
`specs/rules/change-safety.md`.

## Failure modes

- A suspending result written to state without checking it is still relevant (note/workspace
  switched, a newer request superseded it)
- `StateFlow`/`MutableStateFlow` value diverging from a separate `var`/snapshot copy of the
  same truth (two owners)
- Coroutines launched per-event that overlap and complete out of order (last-writer-wins by
  accident)
- Compose composables capturing a value in a lambda that is stale by the time it runs
  (`remember` without the right keys; a callback closing over an old snapshot)
- Non-atomic `MutableStateFlow` updates (`value = value.copy(...)` under concurrency instead
  of `update { }`)
- `collect` in the wrong scope/lifecycle (leaks, or collecting a hot flow past the point the
  result is still wanted)
- A `loadJob` not cancelled before a new one starts, so a completing refresh overwrites a
  freshly-set state (this is exactly the `syncNow()` vs status `loadJob` rule in the sync
  spec)

## Severity guidelines

**Default ordering (rough priority):** races/overwrites in `viewModelScope` → StateFlow vs
snapshot/`var` drift that affects correctness → dependency/`remember`-key gaps that only
matter when values truly change.

### High severity

- Races that can overwrite newer state (sync status, note body, playlist position)
- Flow value vs separate mutable copy divergence that affects rendering or persistence
- Stale captures inside suspending or delayed execution
- A new coroutine that does not cancel, ignore, or supersede outdated in-flight work

### Medium severity

- Missing `remember`/`derivedStateOf` keys that can realistically change over time
- Event handlers launching suspending work using values that may change before it runs
- Non-atomic StateFlow mutation on a path that can be entered concurrently

### Low severity (avoid flagging unless clearly problematic)

- `remember` key completeness for values stable in practice
- Minor style or micro-optimization concerns

### Red flags (always worth calling out)

- Suspending result → `stateFlow.value = result` without verifying relevance
- The same conceptual state stored in both a `StateFlow` and a `var`/snapshot, no stated
  authority
- `value = value.copy(...)` where two coroutines can interleave (use `update { }`)
- A completing background job allowed to clobber a user-initiated state transition

## What to check

- **Suspending / multi-step logic using ViewModel state or params:** Can those values change
  before it completes? Could that cause an outdated write or incorrect UI? Is there a
  request-id / active-key guard, or job cancellation, protecting the write?
- **`viewModelScope.launch` per event:** Can invocations overlap? Can an earlier result
  overwrite a newer one? Is the previous job cancelled when it should be (and *not* cancelled
  when the spec says double-tap should be ignored, e.g. `syncNow()`)?
- **Note / workspace switching:** Can in-flight work apply to the wrong note or workspace?
  Is there an active-id check after the suspension point?
- **StateFlow updates:** Based on previous value under possible concurrency → use atomic
  `update { prev -> ... }`, not `value = value.copy(...)`.
- **Two sources of truth:** Is the same state in both a flow and a `var`/snapshot, or in two
  flows? Which is authoritative? Can they drift? Prefer one owner; if a mirror is needed,
  the sync point must be explicit.
- **Compose:** Are lambdas capturing the latest value (via `rememberUpdatedState` where
  needed) rather than a stale one? Are `remember`/`LaunchedEffect` keys complete? Is state
  hoisted so composables receive values + callbacks, not owning domain state themselves?

## When to ignore

- Stable values that never change (constants, injected singletons)
- Intentionally uncancelled work with documented reasoning
- A `var` used as a deliberate escape hatch with a clear, justified sync strategy
- Safe, documented optimizations

## Examples (bad)

```kotlin
// ❌ Suspending result overwrites state without relevance check
fun loadNote(id: NoteId) = viewModelScope.launch {
    val note = repo.load(id)
    _state.value = _state.value.copy(note = note) // may apply to the wrong note after a fast switch
}

// ❌ Two owners of the same truth that can drift
private var selectedId: NoteId? = null
private val _selected = MutableStateFlow<NoteId?>(null)
fun select(id: NoteId) { selectedId = id } // _selected never updated

// ❌ Non-atomic update under concurrency (lost update)
_state.value = _state.value.copy(count = _state.value.count + 1)

// ❌ Completing refresh clobbers a user action
fun refresh() = viewModelScope.launch { _sync.value = load() } // races syncNow()
```

## Examples (good)

```kotlin
// ✅ Guard the write against a switch
fun loadNote(id: NoteId) = viewModelScope.launch {
    val note = repo.load(id)
    if (id == _state.value.selectedId) {
        _state.update { it.copy(note = note) }
    }
}

// ✅ One owner; derive rather than mirror
private val _selected = MutableStateFlow<NoteId?>(null)
val selected: StateFlow<NoteId?> = _selected.asStateFlow()

// ✅ Atomic update
_state.update { it.copy(count = it.count + 1) }

// ✅ Cancel the stale status job before a user-initiated sync (per sync spec)
fun syncNow() {
    if (_sync.value is Syncing) return // ignore double-tap, do not restart
    statusLoadJob?.cancel()
    syncJob = viewModelScope.launch { runSync() }
}
```
