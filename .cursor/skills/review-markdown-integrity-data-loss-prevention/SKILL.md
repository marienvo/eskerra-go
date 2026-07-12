---
name: review-markdown-integrity-data-loss-prevention
description: >-
  Reviews code that reads, transforms, renders, edits, saves, syncs, or switches
  Markdown notes in the vault. Use when changes touch note persistence, inbox
  save, note switching, frontmatter/body splitting, playlist serialization,
  file writes, or any git-sync flow where user-authored Markdown could be
  silently changed or lost.
---

<!-- REPO-LOCAL skill (eskerra-go) — seeded from notebox's review-markdown-integrity-data-loss-prevention @ c72b677 and made platform-neutral. Not part of the notebox sync set; sync-shared-conventions.sh leaves this directory alone. -->

# Markdown Integrity & Data-Loss Prevention

This skill prioritizes preserving user-authored Markdown exactly unless a change is
explicit, intentional, and reviewable. The vault is a **shared on-disk contract** — the
desktop app and Eskerra Go both read and write the same files — so a silent rewrite here
can corrupt notes the other client authored, over Syncthing/git, where it is hard to notice.

## Safety principle

When correctness is uncertain, the system must fail closed:

- Do not write to disk
- Do not partially transform content
- Prefer surfacing an error over risking corruption

This prevents endorsing "best effort" fixes that trade safety for convenience.

## Core invariant

A note's on-disk bytes must remain identical to the last user-authored content after save,
switch, reload, or sync, unless a deliberate transformation is explicitly part of the
feature. If content changes, the code must make clear **why** it changes, **when** it
changes, and **whether the user can predict or review it**.

## Where this lives in eskerra-go

The high-risk write surfaces (red tier in `specs/rules/change-safety.md`):

- `data/notes/FileNoteWriteRepository.kt`, `core/usecase/SaveNote.kt`, `CreateInboxNote.kt`,
  `DeleteInboxNotes.kt` — the inbox write paths.
- `core/usecase/ManualSyncNow.kt` and the podcast sync channels — commit/merge/push;
  auto-merge writes conflict **sidecars** rather than overwriting (see
  `specs/architecture/sync-hardening-and-recovery.md`).
- `core/playlist/PlaylistMerge.kt` + `WritePlaylist.kt` — playlist serialization/merge that
  must match notebox byte-for-byte on the shared contract.

## Failure modes

- Silent Markdown rewrites (normalization applied on read, or too broadly on write)
- Lost edits during note or workspace switching
- Saving stale editor/UI content over newer on-disk content
- Frontmatter/body split or merge changing unrelated Markdown
- Serialization changing formatting outside its owned region
- Async save/sync races (a coroutine writing after the active note changed)
- Multiple writers for the same note without a single clear authority
- Merge/recovery paths that overwrite the canonical file instead of writing a sidecar

## Severity guidelines

Focus on bugs that can alter, lose, or overwrite user-authored content.

### High severity

- Any path that can overwrite newer user (or other-client) edits with stale content
- Any transformation that silently changes Markdown outside its intended scope
- Any save/sync race during note or workspace switching
- Multiple persistence paths writing the same note without clear authority
- Bypassing the shared git mutex, or destructive recovery (`reset --hard`) on a channel
  the spec says must be fast-forward-only

### Medium severity

- Formatting normalization without a clearly bounded scope
- Parser fallback behavior that may drop or reorder content
- Derived Markdown state stored separately without a synchronization guarantee

### Low severity

- Cosmetic changes inside explicitly owned generated regions
- Internal state changes that cannot affect persisted Markdown

### Red flags (always worth calling out)

- Writing Markdown after suspending/async work without re-checking the active note is still
  the same
- Writing Markdown derived from UI/Compose state instead of the last known persisted or
  editor-authored source
- Rebuilding a whole document to update one section
- Parsing Markdown into a lossy structure and serializing it back
- Normalizing content on read instead of only on explicit write
- Catching a parse error and continuing with partial output
- Staging or committing paths outside the operation's declared scope

## What to check

- **Persistence authority:** Who may write this file? Is there exactly one authoritative
  save path? Can stale content overwrite newer content? Is the git mutex held?
- **Note / workspace switching:** Are pending edits flushed before switching? Can an
  in-flight save or load apply to the wrong note after a fast switch (within milliseconds)?
- **Transformations:** Is the changed region clearly bounded? Does it preserve all unrelated
  Markdown exactly? Is it explicit rather than accidental?
- **Frontmatter:** Does split+merge preserve the body exactly? Are invalid/duplicate keys
  handled safely? Does parser failure avoid writing corrupted output?
- **Serialization:** Deterministic? Only touches owned/generated structures? Unsupported
  shapes preserved or rejected safely (not silently dropped)?
- **Sync/merge:** On divergence, does the code write a conflict sidecar and let remote win
  the canonical file, per spec — never a blind overwrite? Does it stage only its own paths?
- **Error handling:** Does failure prevent unsafe writes? Are partial results kept off disk?
  Is the user shown an explicit conflict/error where needed?

## When to ignore

- Intentional, documented formatting inside explicitly owned regions
- Preview-only rendering (Compose Markdown view) that cannot affect persisted Markdown
- Read-only parsing used only for display
- Test fixtures where normalization is the thing under test

## Examples (bad)

```kotlin
// ❌ Rewrites the whole document to update one section (lossy round-trip)
val updated = serializeMarkdown(parseMarkdown(markdown))
noteWriteRepository.write(path, updated)

// ❌ Suspending save can overwrite newer content after the note switched
suspend fun save(body: String) {
    delay(100)
    noteWriteRepository.write(activePath, body) // activePath may have changed
}

// ❌ Parser failure falls back to empty content, then writes it
val parsed = parseFrontmatter(markdown) ?: Frontmatter(emptyMap(), "")
noteWriteRepository.write(path, merge(parsed))

// ❌ Transformation too broad
noteWriteRepository.write(path, markdown.replace(Regex("""\s+$""", RegexOption.MULTILINE), ""))

// ❌ Writes Markdown derived from UI state instead of the source
noteWriteRepository.write(path, buildMarkdownFromComposeState(uiState))
```

## Examples (good)

```kotlin
// ✅ Update only the owned region
val updated = replaceInboxSection(markdown, nextSection)

// ✅ Guard a suspending save against a note switch
suspend fun save(path: NotePath, body: String) {
    val requestId = ++currentSaveRequestId
    delay(100)
    if (requestId != currentSaveRequestId || path != activeNotePath()) return
    noteWriteRepository.write(path, body)
    markSaved(path)
}

// ✅ Fail closed on unsafe parse
when (val result = parseFrontmatter(markdown)) {
    is Ok -> noteWriteRepository.write(path, merge(result.value))
    is Err -> showParseError(result.error) // do not write
}

// ✅ Divergence writes a sidecar; remote wins the canonical file (per sync spec)
val conflictCopy = writeConflictSidecar(path, localBody)
result.conflictCopies += conflictCopy
```
