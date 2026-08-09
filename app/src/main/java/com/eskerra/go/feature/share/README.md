# feature/share

## Purpose

Receiving a share from another app. The slice owns exactly one decision: what an
inbound share becomes before it reaches the inbox composer. It has **no screen** —
the share lands in the compose pill that already exists in the app shell, and the
user still presses send.

Product rules live in
[`specs/architecture/app-contract.md`](../../../../../../../../../specs/architecture/app-contract.md)
("Share target").

## Key files

- `ShareIntakeViewModel.kt` — takes a `PendingShare`, emits the immediate
  prefill, then optionally a second one once a page title has been fetched.

Everything it decides with is pure and lives in `core/share`
(`BuildShareDraft`, `SharedUrl`, `ShareTitleText`, `ShareTitleUpgrade`,
`SharePrefillMerge`), with the network side behind `core/repository`'s
`PageTitleFetcher` (`data/share/OkHttpPageTitleFetcher`).

## State owner

This ViewModel owns no UI state — it is an event source (`Channel` →
`prefills`, the same shape as the composer's `savedNoteEvents`). The draft
itself stays owned by `CreateInboxNoteViewModel`; this slice only proposes.

It is a ViewModel rather than a composable effect for two reasons: an in-flight
title fetch must survive a rotation, and so must `lastHandledShareId`, which is
what stops the same share being applied twice.

## Rules worth keeping

- **A URL must never reach line 1** of a draft — line 1 is the note's filename.
  The bar for "this is a URL" is deliberately high (`SharedUrl.soleUrlOrNull`).
- **A newer share cancels the older fetch.** Titles are decorations; a stale one
  must not overwrite a newer share's draft.
- **A throwing fetcher is contained.** A share that already works must never take
  the app down because a title lookup misbehaved.
- **The slice never writes.** It proposes text; `CreateInboxNoteViewModel` decides
  whether it lands, and `CreateInboxNote` remains the only writer.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.
