# specs/ — what lives where

Entry point for the documentation system. Agent-facing hard rules live in
[`AGENTS.md`](../AGENTS.md); everything durable or in-flight lives under this folder.

> Provenance: doc-map structure, authority order, and lifecycle rule seeded from
> `notebox` `specs/README.md` @ `c72b677a6334560c12441631189dec576ad771e4` (2026-07-12),
> then adapted to this repo's folders. **Canonical here** — notebox is provenance only,
> not an ongoing authority source, and this file is not part of any sync-set.

**Authority order when sources disagree:** code and tests > ADRs (`adr/`) >
`architecture/` > `AGENTS.md` summaries > plans. Plans contain snapshots that drift;
when a plan contradicts the code, the code is the fact and the plan gets fixed (see
[`plans/README.md`](plans/README.md)).

| Folder | Holds | Durability |
|---|---|---|
| [`adr/`](adr/) | Decisions that would otherwise be re-litigated. One page each. | Durable |
| [`architecture/`](architecture/) | System and subsystem contracts (app contract, boot optimization, sync hardening, git spike). Authoritative detail behind every `AGENTS.md` invariant summary. | Durable |
| [`rules/`](rules/) | Binding conventions tied to enforcement (checks, review checklists, change-safety taxonomy). | Durable |
| [`plans/`](plans/) | Active or intentionally parked plans — **start at [`plans/README.md`](plans/README.md)** for execution order and lifecycle rules. Deleted on absorption. | Temporary |
| [`observability/`](observability/) | Telemetry contract: what Sentry actually sends today (events, tags, fingerprints). Updated in the same change that renames one. | Durable |
| [`team-scalability/`](team-scalability/) | Module-size budget working rules and hotspot inventory. | Durable |

**Lifecycle rule for this whole tree:** a doc survives only while it is the authoritative
home of something. The commit that lands a fact's durable home (code, test, ADR, spec,
rule) deletes or shrinks every other copy in the same change. A doc found wrong twice in
a quarter gets its wrong sections deleted, not scheduled for a rewrite.
