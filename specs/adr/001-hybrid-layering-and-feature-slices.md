# ADR-001: Hybrid layering + feature slices

| Field  | Value |
| ------ | ----- |
| Status | Accepted |
| Date   | 2026-06-11 |

## Context

Eskerra Go is an Android Kotlin/Compose app backed by a git-first, vault-based Markdown store. The roadmap includes note browsing, podcast listening, sync (git for notes, Cloudflare R2 for settings and podcast playback state), and possibly audio recording.

We needed to decide between three structural approaches:

1. **Pure clean architecture** — three horizontal layers: presentation, domain, data.
2. **Pure Vertical Slice Architecture (VSA)** — each feature owns its full stack end-to-end.
3. **Hybrid** — vertical feature slices at the presentation level, shared horizontal layers for infrastructure that multiple features depend on.

## Decision

We use the **hybrid** approach:

- **Vertical slices** at the presentation level: each screen lives in its own `feature/<name>/` package (screen + ViewModel + UI state). Slices do not depend on each other.
- **Horizontal layers** for shared domain and infrastructure: `core/` (models, repository interfaces, use cases) and `data/` (implementations: git, notes, credentials, workspace, R2).
- `app/` is the composition root; `ui/` holds theme and shared UI primitives.

```
com.eskerra.go/
├── core/        domain: model, repository interfaces, use cases, wikilink
├── data/        infrastructure: git, notes, credentials, workspace, r2
├── feature/     slices: inbox, note, editor, add, sync, setup, menu, podcasts
├── ui/          theme, shared UI
└── app/         composition root
```

## Consequences

### Why not pure clean architecture?

Features vary mostly in UI and screen state. A single `presentation/` layer would couple unrelated screens, fight the module-size budgets (see [`specs/team-scalability/README.md`](../team-scalability/README.md)), and make per-feature test isolation harder to enforce.

### Why not pure VSA?

The note features (inbox, note, editor, add, sync) share one underlying model: a git-backed Markdown vault. Pure VSA would have each slice own its path to data, leading to either duplication of JGit calls, file I/O, and wiki-link parsing, or a de facto shared layer without explicit structure. Two properties force the shared core:

1. **Fragile shared infrastructure.** The git working tree (locks, refs, checkout state) is one shared resource; independent per-feature access leads to sync corruption (see [`sync-hardening-and-recovery.md`](../architecture/sync-hardening-and-recovery.md)). Hence: "Git operations live only in `data/git`".
2. **Cross-cutting domain rules.** "Inbox notes editable, all other notes read-only" spans multiple slices and must live in one place (`core`), not be copied and allowed to drift.

Layering also enables JVM unit tests for domain/data logic (`testDebugUnitTest` quality gate) without instrumented tests — enforced via the AGENTS.md rules "UI must not read files", "ViewModels must not depend on Android Context".

## Placement rules

Use these to decide where new code goes:

- **Shared across features, or touching fragile shared infrastructure** (git working tree, vault filesystem, credentials, R2 objects) → interface in `core/repository`, implementation in `data/<area>`, orchestration in `core/usecase`.
- **Owned by exactly one feature** → the slice may own its domain and data inside `feature/<name>/`, subject to the same internal rules: composables receive state and callbacks only; the ViewModel talks to a repository/use case; I/O lives behind an interface for JVM testability.
- **Promotion rule:** a slice-owned model moves to `core`/`data` when a second feature needs it. Features must never reach into another feature's package.

## Roadmap mapping

| Capability | Placement | Rationale |
| --- | --- | --- |
| Notes browsing (git) | Existing `core` + `data/notes` + `data/git` | Shared vault model. |
| R2 transport (S3 HTTP, ETag poller) | `core` interface + `data/r2` | Both vault settings and podcast playback state use the same transport, credentials, and merge/poll contract. One client, one credential path. |
| Vault settings (`.eskerra/settings-*.json`) | `core` + `data` | Read by multiple features (setup, sync, podcasts). On-disk contract is vault-wide. |
| Podcast listening (playback, playlist UI) | `feature/podcasts/` | No other slice reads playback state. The slice *consumes* the shared R2 transport and settings; it does not implement them. |
| Audio recording (future) | Own slice for recording state; recorded files enter the vault through the existing write repositories | Recording state is slice-owned; the produced artifact lands in shared vault territory, so the write path is shared. |

**The dividing line:** transport and on-disk/remote contracts are shared layers; feature behaviour on top of them is slice-owned.

## Related

- Enforced rules: [`AGENTS.md`](../../AGENTS.md) (project-specific rules section).
- App contract: [`app-contract.md`](../architecture/app-contract.md).
