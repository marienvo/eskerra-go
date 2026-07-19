---
name: review-architecture-drift-responsibility-boundaries
description: >-
  Reviews Kotlin/Android code for creeping responsibilities, unclear ownership,
  cross-layer leakage, god modules, and architectural drift within the hybrid
  layering + feature slices. Use when changes touch ViewModels, use cases,
  repositories, the git/notes/search/credentials adapters, feature-slice wiring,
  Today Hub, workspace/gate, or the shared core.
---

<!-- REPO-LOCAL skill (eskerra-go) — written fresh for Kotlin/Android; the notebox counterpart is React/Tauri-specific. Not part of the notebox sync set; sync-shared-conventions.sh leaves this directory alone. Edit it here. -->

# Architecture Drift & Responsibility Boundaries

This skill prioritizes keeping responsibilities explicit, local, and reviewable. It flags code that
works today but makes the system harder to reason about tomorrow. It prefers clear, slightly
imperfect structure over clever abstractions that hide responsibility.

## Why it exists

Eskerra Go uses hybrid layering + feature slices ([specs/adr/001-hybrid-layering-and-feature-slices.md](../../../specs/adr/001-hybrid-layering-and-feature-slices.md)):
`ui` / `feature` → `core` (domain: markdown, playlist, podcast, search, todayhub, usecase, vault,
wikilink) with `data` adapters (git, r2, notes, credentials, search) behind interfaces. **ArchUnit
tests enforce the allowed dependency directions mechanically.** This skill is the *judgment*
companion to those tests: ArchUnit proves `ui` doesn't import `data`, but it cannot tell you a
`ViewModel` has quietly become a god object, that a use case now owns both policy and persistence,
or that a "small helper" became a cross-layer backdoor. Catch the drift the layer test can't assert.

## Core principle

A change should make ownership **clearer, not blurrier**. A module should not need to change for
unrelated features. When a module gains responsibility, the code should make clear: what moved or
was added, why this module is the right owner, which layer stays authoritative, and whether a new
dependency direction was created.

## Failure modes (eskerra-go)

- **God ViewModel** accumulating gate logic, sync orchestration, note loading, and playlist polling
  in one class (see the boot/sacred-path surfaces in [specs/architecture/boot-optimization.md](../../../specs/architecture/boot-optimization.md)).
- **UI reaching past its seam** — a Composable or ViewModel touching files, `File`/IO, or JGit
  directly instead of going through a repository/use case. (ADR-001: UI must not read files or call
  Git; Git lives only in `data/git`; ViewModels depend on repos/use-cases, not `Context`.)
- **Core depending on Android/platform** — a `core` type importing `android.*`, `Context`, JGit, or
  a `data`-layer concrete class instead of a domain interface.
- **Adapter logic leaking into domain** — parsing, path/skip rules, serialization, or merge policy
  living in `data` when it is domain logic (or vice versa). Note the **PlaylistMerge must match
  notebox verbatim** invariant — merge policy belongs in `core`, not smeared across adapters.
- **Two owners for one decision** — the note-registry cache, gate fingerprint, or sync branch state
  written authoritatively from more than one place.
- **Convenience backdoors** — a `data`/util helper that a feature starts depending on to bypass the
  established use-case seam because it is faster locally.
- **Budget-driven smearing** — splitting a file only to dodge the module-size cap
  (`./scripts/check-module-budgets.sh`) by scattering one responsibility across siblings, rather
  than extracting a cohesive unit.

## Severity guidelines

Focus on changes that increase long-term coupling or make correctness harder to verify.

### High severity
- A ViewModel/use case starts owning multiple unrelated concerns without clear justification.
- UI or a ViewModel writes files / calls JGit / reaches sync directly, bypassing the repo/use-case
  seam (also an ArchUnit violation — but call out *why* it matters, not just that a test fails).
- A `core` type imports `android.*`, `Context`, JGit, or a concrete `data` class.
- Two modules can now make conflicting decisions about the same state or file.
- A shortcut bypasses validation, serialization, the git mutex/channel rules, or conflict handling.

### Medium severity
- A helper placed in a layer where it pulls dependencies in the wrong direction.
- A callback/lambda chain that makes control flow hard to trace across ViewModel → use case → repo.
- Logic duplicated across layers with slight differences.
- A class/file name that no longer describes what it does after the change.

### Low severity
- Minor local duplication that avoids premature abstraction.
- Temporary, clearly isolated glue with a documented removal path.
- Small colocated logic that improves readability without crossing a layer boundary.

### Red flags
- "Just inject `Context`/the repo into the Composable for now."
- "This is only needed here" — a `data` helper imported straight into a feature.
- A `core` class importing an adapter or an Android type.
- A ViewModel that both decides policy and performs persistence/IO.
- A use case that must be edited for an unrelated feature.
- A helper whose name hides a write/sync side effect.
- A file split purely to satisfy the budget, leaving one responsibility across several files.

## What to check

- **Responsibility ownership:** what is this class responsible for? Did the change add a new
  responsibility? Is it cohesive with the existing owner, or does it belong in a use case/repo?
- **Layer boundaries (ADR-001):** does UI stay presentational (state in, callbacks out)? Does
  `core` remain platform-independent? Do `data` adapters isolate JGit/R2/IO behind interfaces? Are
  dependencies flowing `ui/feature → core`, `data → core`, never the reverse?
- **Established seams:** does the change use the existing use-case/repository path, or introduce a
  new one that bypasses orchestration, the git single-mutex, validation, or serialization? If it
  adds a seam, is the reason explicit?
- **Control/data flow:** can a reviewer trace where a decision is made? Are there multiple
  authorities for the same state (cache, fingerprint, branch)? Are lambdas hiding ordering/ownership?
- **Abstractions:** does the abstraction reduce coupling or only hide it? Surprising side effects?
  Would it still make sense used twice?
- **Future change cost & tests:** would an unrelated feature now need to touch this module? Does the
  change make the domain harder to unit-test in isolation (every feature slice ships ≥1 unit test)?

## When to ignore

- Simple local code intentionally not abstracted yet.
- Duplication clearer and safer than a premature abstraction.
- Purely presentational UI orchestration.
- Temporary migration glue with a clear removal path.
- Test-only helpers that do not leak into production architecture.
- Pragmatic shortcuts that are clearly local, bounded, and add no new cross-layer dependency.

## Examples (bad)

```kotlin
// ❌ ViewModel reaches past its seam — file IO + JGit directly (ADR-001 violation)
class NoteViewModel(app: Application) : AndroidViewModel(app) {
    fun save(path: String, body: String) = viewModelScope.launch {
        File(path).writeText(body)                 // UI/VM must not touch files
        Git.open(File(vaultDir)).commitAll(path)   // Git only in data/git
    }
}

// ❌ core depends on the platform + a concrete adapter
package com.eskerra.go.core.usecase
import android.content.Context
import com.eskerra.go.data.git.JGitClient          // core must not know data/platform
class LoadNote(val ctx: Context, val git: JGitClient)

// ❌ God ViewModel: gate + sync + notes + playlist in one place
class AppViewModel : ViewModel() {
    fun resolveGate() { /* ... */ }
    fun syncNow() { /* ... */ }
    fun loadInbox() { /* ... */ }
    fun advancePlaylist() { /* ... */ }
}

// ❌ Merge policy leaking into an adapter (must match notebox verbatim, lives in core)
package com.eskerra.go.data.r2
fun mergePlaylists(a: Playlist, b: Playlist): Playlist { /* domain rule in the adapter */ }
```

## Examples (good)

```kotlin
// ✅ UI/VM delegates to a use case; persistence + Git stay behind the seam
class NoteViewModel(private val saveNote: SaveNote) : ViewModel() {
    fun save(id: NoteId, body: String) = viewModelScope.launch { saveNote(id, body) }
}

// ✅ core depends on domain interfaces only; the adapter implements them
package com.eskerra.go.core.usecase
class LoadNote(private val notes: NoteRepository)      // interface owned by core

// ✅ Cohesive owners: gate, sync, inbox, playlist each behind their own use case/VM
class SyncViewModel(private val syncWorkspace: SyncWorkspace) : ViewModel()

// ✅ Merge policy owned by core, shared by every client (matches notebox)
package com.eskerra.go.core.playlist
object PlaylistMerge { fun merge(a: Playlist, b: Playlist): Playlist { /* single authority */ } }
```
