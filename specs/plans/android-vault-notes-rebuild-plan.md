# Android vault notes rebuild — implementation plan (eskerra-go)

Implementation plan for [`android-vault-notes-rebuild-spec.md`](./android-vault-notes-rebuild-spec.md) in the **eskerra-go** Kotlin/Compose app.

This plan adapts the spec (written against the React Native `apps/mobile` app) to this repo's architecture. It is **not** a port: the RN app and this app share *on-disk behavior*, not implementation.

---

## 0. Decisions taken (from planning interview)

| Topic | Decision | Consequence |
| ----- | -------- | ----------- |
| Vault access | **Git working tree, not SAF.** The vault is a JGit checkout in the app files dir; notes are addressed by **plain relative paths** (`NoteId`/`NotePath`), read through `java.io.File`. | The spec's `content://` SAF conventions (§2.3), the local-image "known gap" (§13.2), and SAF document-ID delete fallbacks (§7.1) **do not apply**. Local images resolve to `file://` and just load. |
| Vault search (§12) | **Full native FTS5.** | Contradicts current `AGENTS.md` rule "No full-text search in the PoC" — that rule must be amended (Phase 0). Uses Android's bundled SQLite FTS5; no Rust/RN native module. |
| Read-only renderer (§8) | **Compose markdown library + a thin Eskerra preprocessing/extension layer.** | Adopt a maintained Compose-native markdown renderer; layer Eskerra-specific syntax (wiki links, callouts, reminder pills, vault image/link resolution) on top via preprocessing + custom rules. |
| Custom inline styling | **Reminder date-time pills** (`@2025-12-20_1700`) and struck variants, mirroring desktop `notebox`. | Needs a custom inline rule + a `kotlinx-datetime`-based tone classifier. See §"Reminder pills" below. |
| Staging | **Phased, full coverage.** | One plan, ordered phases, tests per phase. |

---

## 1. Architectural deltas vs the spec

The spec assumes the RN app. Translate as follows:

| Spec concept | eskerra-go equivalent |
| ------------ | --------------------- |
| SAF tree URI / `content://…/Inbox/note.md` | `NoteId` = vault-relative path; working tree under `WorkspaceConfig` |
| `react-native-saf-x` listing | `MarkdownNoteScanner` walking the working tree |
| `@eskerra/core` TS modules | `core/` Kotlin equivalents (some already exist: `core/wikilink`, `core/usecase`, `core/vault`) |
| `inboxContentByUri` in-memory cache | `core/repository` cache (extend `NoteContentRepository`/snapshot stores) |
| `react-native-markdown-display` + custom rules | Compose markdown lib + Eskerra rule layer |
| Native SQLite `vault_markdown_notes` | Optional; registry is already an in-memory scan. FTS5 index is the new SQLite surface |
| AsyncStorage (`activeTodayHubStorage`) | DataStore (pattern already used: `DataStoreLocalSettingsStore`, `DataStoreWorkspaceStore`) |

**Placement** follows ADR-001: shared vault contracts → `core/repository` interface + `data/<area>` impl + `core/usecase` orchestration; per-screen state → `feature/<name>/`. Git stays in `data/git`; UI never reads files or calls git.

---

## 2. Current state inventory (what already exists)

Already implemented and reusable:

- **Inbox I/O.** `feature/inbox`, `feature/add`, `feature/editor`; use cases `CreateInboxNote`, `SaveNote`, `LoadInboxSummaries(+Cached)`, `LoadEditableNote`; `data/notes/FileNoteWriteRepository`, `FileInboxSnapshotStore`. Compose model lives in `core` (`InboxNoteDraft`).
- **Registry scan.** `data/notes/MarkdownNoteScanner` → `NoteRegistry` of `NoteSummary` (title from first H1, snippet, `isInbox`, mtime).
- **Wiki links.** `core/wikilink/WikiLinkParser` + `WikiLinkResolver` (exact/case-fold stem, path-shaped, ambiguous, traversal guard). `LoadNoteForReading` parses + resolves and emits `NoteReaderSegment`s.
- **Reader screen.** `feature/note/NoteScreen` renders an `AnnotatedString` of text + wiki-link segments.
- **Vault layout + settings.** `core/vault/VaultLayout`, settings codecs, R2 transport.

### Critical gap

`NoteScreen` **does not render markdown** — it emits plaintext plus clickable wiki segments. No headings, bold, lists, code, blockquotes, tables, callouts, or images. The spec's entire §8 shared renderer is effectively greenfield. The existing `NoteReaderSegment` pipeline is wiki-link-only and will be **subsumed** by the new preprocessing-based renderer (Phase 3).

---

## 3. Gap analysis (spec section → status)

| Spec § | Area | Status | Phase |
| ------ | ---- | ------ | ----- |
| 2.2 | Vault visibility filter (`Assets`/`Excalidraw`/`Scripts`/`Templates`/dot/sync-conflict at any depth) | **Missing** — scanner only skips `.git` | 1 |
| 4 | Inbox list (sort, sync-conflict exclude, tile color, relative date label) | Partial — list+sort exist; **tile color + relative-date label missing** | 2 |
| 5 | Inbox detail (callout-only renderer, frontmatter strip, flat blue links) | Partial — needs frontmatter strip + callouts; **must stay the limited renderer, not the §8 one** | 3 |
| 6 | Compose/create/edit (H1 shape, sanitize, dedup `-2`, mkdir, round-trip) | Mostly exists — **verify dedup + sanitize parity against core test vectors** | 2 |
| 7 | Delete (Inbox-only guard, multi-select, no trash) | Partial — **verify `isNoteUriInInbox` equivalent + stale-entry error copy** | 2 |
| 8 | Shared read-only renderer (markdown + callouts + tables + link colors) | **Missing (core feature)** | 3 |
| 9 | Wiki nav + relative `.md` links + external links | Partial — resolver exists; **relative-link href resolution, external-link open, ambiguous picker UI missing** | 4 |
| 11 | Today Hub (discovery, frontmatter, week rows, column split, week nav, progress strip) | **Missing (entire feature)** | 6 |
| 12 | Vault search (FTS5 index, ranking, query UX) | **Missing (entire feature)** | 7 |
| 13 | Images (local attachment resolution + remote) | **Missing** | 5 |
| 10 | Dark-mode chrome contract | Partial — theme exists; **audit against token table** | per-phase |

---

## 4. Cross-cutting building blocks

Several spec behaviors are shared and should land as pure `core` modules (JVM-testable, no Android deps), reused across reader/inbox/Today Hub:

- **`core/vault/VaultVisibility`** — `isEligibleVaultMarkdownPath`, excluded-dir set, sync-conflict matcher (`*.md.sync-conflict-*`), dot-prefix rule applied at **every** path segment. Mirrors `vaultVisibility.ts`. (Phase 1)
- **`core/inbox/InboxTileColor`** — blue-gradient decay over ~4 weeks → neutral gray, from mtime+now. Mirrors `inbox/inboxTileColor.ts`. (Phase 2)
- **`core/datetime/RelativeCalendarLabel`** — `Today`/`Yesterday`/weekday/`YYYY-MM-DD`/em-dash. Uses `kotlinx-datetime`. Mirrors `relativeCalendarLabel.ts`. (Phase 2)
- **`core/markdown/VaultMarkdownPreprocess`** — wiki `[[inner]]` → synthetic link **outside fenced code only** (mirrors `preprocessVaultReadonlyMarkdownBody`); frontmatter split (`splitYamlFrontmatter`). (Phase 3)
- **`core/markdown/CalloutHeader`** — `> [!type]` parse + resolve (mirrors `calloutHeader.ts`). (Phase 3)
- **`core/markdown/DateToken`** — reminder token parse/format + tone (see below). (Phase 3)
- **`core/markdown/VaultLink`** — relative `.md` href resolution (`./`,`../` vs vault-root vs `Inbox/` rules), external-link classification (mirrors `vaultRelativeMarkdownLink.ts`). (Phase 4)
- **`core/attachments/AttachmentPaths`** — vault-relative image src → vault-absolute path; allowed extensions (mirrors `attachments/attachmentPaths.ts`). (Phase 5)

Each gets a unit test seeded from the corresponding `@eskerra/core` test vectors in `notebox` (`inboxComposeNote.test.ts`, `wikiLinkInbox.test.ts`, `todayHub.test.ts`, `vaultSearchHighlight.test.ts`, `attachmentPaths.test.ts`) — copy the golden cases.

### Reminder pills (custom styling)

Canonical source: `notebox/apps/desktop/src/editor/noteEditor/dateToken/dateToken.ts`.

- **Syntax:** live `@YYYY-MM-DD` or `@YYYY-MM-DD_HHMM` (e.g. `@2025-12-20_1700`); struck `@~~YYYY-MM-DD(_HHMM)?~~` (daemon may insert `\_`).
- **Patterns:** `DATE_TOKEN_PATTERN = /(?:^|\s)(@\d{4}-\d{2}-\d{2}(?:_\d{4})?)/g`; struck variant analogous. Port verbatim.
- **Tone buckets** (`dateTokenPillTone`): `completed` (struck) / `past` / `urgent` (timed today, same daypart) / `future` (later day or later daypart) / `neutral`. Daypart = morning/afternoon/evening. Port `isDateTokenInPast`/`isDateTokenFuture`/`daypartOfMinutes`.
- **Pretty label:** `formatDateTokenPretty` (e.g. "Tom 12:00"). Port.
- **Render:** custom inline rule emits a styled pill (rounded background per tone) instead of raw token text. Dark-mode tone colors to be defined in `ui/theme` (derive from desktop CSS pill tones).

This is a **read-only** concern here (no picker/editing of tokens in the PoC); pills appear in the §8 renderer and Today Hub cells.

---

## 5. Phased plan

Each phase is independently shippable, gated by `./scripts/check-module-budgets.sh` + `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`, and adds tests. Keep new `.kt` files ≤400 lines (ADR/budget rule); split renderer rules per concern.

### Phase 0 — Foundations & dependencies
- Amend `AGENTS.md`: replace "No full-text search in the PoC" with the FTS5 decision (cite this plan); note the markdown-render dependency.
- Add to `gradle/libs.versions.toml` + `app/build.gradle.kts`:
  - A **Compose markdown renderer** (recommend evaluating *Multiplatform-Markdown-Renderer* / JetBrains `markdown` parser — Compose-native, supports custom annotators/renderers and GFM tables). Confirm: custom inline/block rules, table support, selectable text, license.
  - **Coil** (`coil-compose`) for images (Phase 5), supports `file://`.
  - SQLite FTS5 needs **no new dependency** — Android's bundled SQLite supports FTS5 (`SQLiteOpenHelper`/`SupportSQLiteOpenHelper`). Decide raw `SQLiteOpenHelper` vs Room (Room adds codegen but eases reconcile queries); recommend raw helper to keep the FTS schema explicit.
  - `kotlinx-datetime` for relative labels / token tone / Today Hub week math.
- No behavior change; just wiring + a smoke test that the lib renders a heading.

### Phase 1 — Vault visibility filter (§2.2)
- New `core/vault/VaultVisibility` (pure) + tests (excluded dirs at depth, dot-prefix per segment, sync-conflict suffix).
- Wire into `MarkdownNoteScanner` (replace the `.git`-only skip with the full filter, applied in `preVisitDirectory` and `visitFile`).
- Result: registry and every downstream surface exclude `Assets/Excalidraw/Scripts/Templates`, dotfiles, sync-conflict files.

### Phase 2 — Inbox parity polish (§4, §6, §7)
- `core/inbox/InboxTileColor` + `core/datetime/RelativeCalendarLabel` (pure + tests from vectors).
- Inbox list: render avatar tile color + relative-date meta; exclude sync-conflict filenames in listing; confirm sort (mtime desc, name tie-break).
- Compose: verify `sanitizeFileName`, `pickNextInboxMarkdownFileName` (`-2`,`-3`), title-only `# T\n` vs title+body `# T\n\n{body}`, and `inboxMarkdownFileToComposeInput` round-trip against `inboxComposeNote.test.ts` vectors — add any missing cases as Kotlin tests.
- Delete: confirm Inbox-only guard (relative-path prefix check replaces `isNoteUriInInbox`), multi-select resolution, stale-entry error string, legacy "belongs to Log" copy.

### Phase 3 — Shared read-only markdown renderer (§5, §8) — core deliverable
- Build `core/markdown/`: `VaultMarkdownPreprocess` (frontmatter strip + wiki→synthetic link outside fences), `CalloutHeader`, `DateToken` (pills). All pure + tested.
- `feature/note` (or new `ui/markdown/`) renderer composables:
  - Standard markdown via the lib: headings, bold/italic/strike, lists, blockquotes, fenced/inline code, HR, **tables** (horizontal scroll), paragraphs.
  - Custom rules: **callouts** (`> [!type]`), **reminder pills**, **wiki/internal links** (color `#FF8A82`), **external links** (`#7DCCFF`, open via `Intent`), **muted unresolved** (`#cfcfcf`). Body `#f5f5f5`; code bg/border per §8.4.
  - Internal-link resolution state: loading→optimistic internal color, error→muted (§8.3).
- **Inbox detail (§5)** is a *separate, limited* renderer: callouts only, flat blue `#4f9dff` links, **no** wiki resolution / vault rules / images. Keep it deliberately minimal; do **not** route it through the §8 renderer.
- Retire the plaintext `NoteReaderSegment` path in `NoteScreen`, replacing it with the new renderer fed by preprocessed markdown + the resolved-link registry. Keep `WikiLinkResolver` for resolution; the segment model is replaced by in-renderer link annotation.

### Phase 4 — Wiki navigation & relative/external links (§9)
- `core/markdown/VaultLink`: `resolveVaultRelativeMarkdownHref` (source-dir rules: `./`,`../` vs vault root vs `Inbox/`), non-md/empty/unresolvable classification.
- Extend resolution to feed renderer link rules; wire internal-link taps to navigate (reader push), external to browser, ambiguous → **picker sheet** (`ui` modal, `#1d1d1d` per §10) listing candidate titles+paths.
- Error/affordance copy: "Note not found", "Still indexing vault", index-unavailable + Retry.
- Tests: `[[Note]]`, `[[Note|Alias]]`, `[[https://x]]`, `[text](../Inbox/other.md)`, path-shaped wiki, fence-protected wiki, ambiguous ≥2.

### Phase 5 — Images (§13)
- `core/attachments/AttachmentPaths`: allowed extensions; resolve `src` relative to **note directory** → vault-absolute path; pass-through `http(s)`/`data:`.
- Custom image rule + Coil: vault-absolute → `file://` load; scale to width, preserve aspect, alt as a11y label; SVG optional/placeholder; broken placeholder otherwise.
- Tests for path resolution; manual/instrumented check for `file://` render.

### Phase 6 — Today Hub (§11)
- `core/todayHub/` (pure, heavily tested from `todayHub.test.ts`):
  - Hub discovery (`Today.md` stems), folder-label from parent dir.
  - `parseTodayHubFrontmatter` (`perpetualType: weekly`, `start`, `columns`); column count = `1 + columns.length`.
  - Week math: `enumerateTodayHubWeekStarts` (53 anchors), Monday-stem-per-`start`, week-range label, `todayHubWeekProgress`.
  - Row column split: port `splitMergeTodayRowColumns` **tolerant regex** exactly (`/(?:\n\n|\n)[ \t]*::today-section::[ \t]*(?:\n\n|\n(?=[^\n])|$)/g`), `\r\n`→`\n` normalize, overflow-into-last-column, delimiter-only-line strip.
- `feature/todayhub/` slice: hub state, week prev/next limited to on-disk stems, picker modal (>1 hub), persist active hub URI in **DataStore**, column UI with progress strip, intro + cells via the §8 renderer (cells use synthetic row URI for relative-link/image base dir). Read-only (no row editing — non-goal §16).
- New navigation entry (Today/Vault tab). Reuses Phase 3 renderer + Phase 5 images + reminder pills.

### Phase 7 — Vault search (§12)
- `data/search/` SQLite FTS5 index: schema `vault_search_notes(uri UNINDEXED, rel_path, title, filename, body)`, tokenizer `unicode61 remove_diacritics 2`; index file at `filesDir/vault-search-index/{sha1(canonicalBase)}.sqlite`; schema-version table → rebuild on mismatch.
  - Phased fill: titles first (`indexReady`), then bodies (`bodiesIndexReady`).
  - Incremental reconcile by `(size, lastModified)`; `touchPaths` on inbox create/write/delete (hook into Phase 2 mutation side-effects, async/non-blocking).
- `core/search/` (pure, tested): `Fts5Query` builder (whitespace tokens → quoted phrases, strip `"()\\`, drop `and|or|not|near`, implicit AND); `SearchRanker` tiers (title/path exact 40k, prefix 25k, fuzzy 12k, body BM25×0.02; bestField title>path>body; matchCount); snippet (first matching body line, ≤160 chars, 1-based line no.); `vaultSearchHighlightSegments`.
- `feature/search/` slice: 260 ms debounce, cancel prior, hold previous 100 ms, status lines, result rows (title/path highlight + snippet), tap → reader. Stale-event safety (`searchId`+`vaultInstanceId`).
- Index warm: after vault session exists + on search open; foreground 5-min reconcile (AppState/lifecycle). Background WorkManager reconcile **optional** (mark deferred — respects "no background sync" spirit; foreground-only is acceptable for PoC).

### Phase 8 — Dark-mode chrome audit (§10) & acceptance pass
- Audit theme tokens against §10 table (header text `#ffffff`, dividers `#333333`, modal `#1d1d1d`, etc.) in `ui/theme`.
- Run the §15 acceptance scenarios as automated tests where feasible; manual checklist for image/render/Today Hub.

---

## 6. Module placement summary (ADR-001)

| New code | Package |
| -------- | ------- |
| Pure vault/markdown/search/today/attachment logic | `core/<area>` (no Android deps, JVM-tested) |
| FTS5 index + reconcile | `data/search` (interface in `core/repository`) |
| Image loading wiring | `feature`/`ui` (Coil); path math in `core/attachments` |
| Renderer composables | `ui/markdown` (shared) consumed by `feature/note`, `feature/todayhub` |
| Today Hub / Search screens + state | `feature/todayhub`, `feature/search` |
| Persisted active-hub URI | `data` DataStore store + `core/repository` interface |

No feature slice reaches into another; shared logic is promoted to `core`/`data` per the promotion rule.

---

## 7. Risks & open questions

1. **Markdown lib fit.** Must support GFM tables, custom inline/block rules (callouts, pills, wiki/vault links, images), and selectable text in Compose. If the chosen lib can't express all custom rules, fall back to a parse-tree walk over the JetBrains `markdown` AST rendered to Compose ourselves (still "library-assisted", not fully custom). Validate in Phase 0 with a spike before committing Phases 3–6.
2. **AGENTS.md conflict.** "No full-text search" must be formally amended (Phase 0) — flagged because it's an enforced project rule, not just docs.
3. **Reminder-pill scope.** Read-only rendering only here; no token picker/editing (desktop-only). Confirm dark-mode pill tone palette with design (derive from desktop CSS).
4. **Background reconcile.** Spec mentions WorkManager; AGENTS.md says "no background sync in the PoC". Plan keeps search reconcile **foreground-only**; background is explicitly deferred.
5. **`NoteReaderSegment` retirement.** Phase 3 replaces the existing reader rendering path — ensure no other consumer depends on the segment model before removing it (currently only `NoteScreen`).
6. **Module budgets.** The renderer + FTS ranker risk exceeding 400 lines; pre-plan splits (one file per custom rule; ranker tiers vs query builder vs snippet separate).

---

## 8. Suggested commit/PR cadence

One PR per phase (Phase 0 may bundle with Phase 1). Each PR: pure-core logic + tests first (red→green), then UI wiring, then the quality gate. This mirrors the existing phase-numbered commit history on this branch.
