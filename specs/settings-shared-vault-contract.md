# Shared Settings and Vault Contract

## Purpose

This document is the portable settings contract for Eskerra clients. It is intended to be copied
or referenced from the Android/Kotlin repository as context for how desktop and mobile should
share vault settings, R2 connection metadata, and security rules.

The key principle is simple: the synced vault contains inspectable non-secret configuration and
content. Device-local stores contain only device-local state. Secrets are never written to the
vault or to app-managed plaintext files.

## Storage Overview

| Location | Syncs between devices? | Contents | Authority |
|---|---:|---|---|
| `.eskerra/settings-shared.json` | Yes | Shared non-secret vault configuration | Authoritative for shared settings |
| `.eskerra/settings-local.json` | No | Device identity and local playlist watermarks | Authoritative for that device only |
| OS keychain / Android Keystore | No | R2 access key id and secret access key | Authoritative for that device only |
| App-local cache/store | No | Disposable first-paint cache and transient retry state | Never authoritative |
| R2 `playlist.json` | Cross-device cloud object | Playlist playback state when R2 is configured | Authoritative for playlist sync |
| R2 `theme-preference.json` | Cross-device cloud object | Theme preference in the current desktop v1 path | Theme-only legacy/special path |

No app-managed cache may become the source of truth for a setting. Losing the cache must not lose
settings.

## Shared Settings File

The shared settings file is:

```text
.eskerra/settings-shared.json
```

The directory name `.eskerra` and the filename `settings-shared.json` are fixed contract. They are
not configurable through vault layout settings.

Clients should read the whole JSON object, preserve unknown keys, and write with read-modify-write
semantics. A client must not rewrite the document from a narrowed typed model that drops fields it
does not understand.

Current v1 shape:

```jsonc
{
  "r2": {
    "endpoint": "https://<account>.r2.cloudflarestorage.com",
    "bucket": "bucket-name",
    "jurisdiction": "eu"
  },
  "appSettings": {
    "version": 1,
    "vaultLayout": {
      "inboxFolder": "Inbox",
      "generalFolder": "General",
      "assetsFolder": "Assets",
      "attachmentsSubfolder": "Attachments",
      "hubNoteFileName": "Today.md",
      "podcastFeedFileSuffix": "podcasts.md",
      "podcastEpisodeFilePrefix": "📻 "
    }
  },
  "themePreference": {
    "themeId": "default",
    "mode": "auto"
  },
  "frontmatterProperties": {},
  "linkSnippetBlockedDomains": []
}
```

All fields are optional unless noted by the specific section below. Missing fields resolve through
client defaults.

## Sparse `appSettings`

`appSettings` is a sparse object. It should contain only values that differ from client defaults.
The example above shows defaults for clarity, but a real file can be as small as:

```json
{
  "appSettings": {
    "vaultLayout": {
      "inboxFolder": "Notes"
    }
  }
}
```

Rules:

- Missing setting: use the default.
- Invalid setting value: fall back to the default for that setting only.
- Invalid one setting must not make the whole document unreadable.
- Unknown keys must be preserved on write.
- Values equal to defaults should be omitted when writing.
- Changing a default in app code is a behavior migration and must be treated deliberately.

Desktop implements setting descriptors in `packages/eskerra-core/src/appSettings.ts`. A Kotlin
client should mirror the same defaults and validation rules for the settings it consumes.

## Vault Layout Settings

`appSettings.vaultLayout` is the shared vault layout contract.

Defaults:

| Field | Default | Meaning |
|---|---|---|
| `inboxFolder` | `Inbox` | Workspace inbox / capture folder for user notes |
| `generalFolder` | `General` | General content folder; currently hosts Today Hub workspaces and podcast/feed notes |
| `assetsFolder` | `Assets` | Top-level assets folder |
| `attachmentsSubfolder` | `Attachments` | Attachments subfolder inside `assetsFolder` |
| `hubNoteFileName` | `Today.md` | Today Hub note filename |
| `podcastFeedFileSuffix` | `podcasts.md` | Suffix identifying podcast feed markdown files |
| `podcastEpisodeFilePrefix` | `📻 ` | Prefix identifying cached podcast episode markdown files |

Validation:

- Folder fields must be a single safe path segment.
- Folder fields must be non-empty after trim.
- Folder fields must not contain `/`, `\`, control characters, `.`, `..`, or `.eskerra`.
- `hubNoteFileName` must be a single safe filename ending in `.md` and must have a non-empty stem.
- `podcastFeedFileSuffix` must be non-empty, must not contain path separators, and must end in `.md`
  case-insensitively.
- `podcastEpisodeFilePrefix` must be non-empty, must not contain path separators or control
  characters, and must not contain `.md`.
- The trailing space in the default podcast episode prefix is meaningful.

Behavior invariant:

Changing vault layout never renames, moves, deletes, migrates, or cleans up existing files or
folders in v1. It only changes where the client looks and where new files/folders are created.

For example, if `inboxFolder` is changed from `Inbox` to `Notes`, clients should list and create
inbox notes under `Notes/`. They must not move old files out of `Inbox/`, and they must not keep
treating `Inbox/` as special unless a specific feature has an explicit legacy alias rule.

The known explicit legacy alias is wiki-link syntax: `[[Inbox/foo]]` remains stable link text and
should resolve to the configured inbox folder. Existing markdown links must not be rewritten to the
custom folder name.

## Fixed Vault Contract

These names or formats are intentionally not configurable:

- `.eskerra/`
- `settings-shared.json`
- `settings-local.json`
- `playlist.json`
- `theme-preference.json`
- `.md` extension
- `sync-conflict` marker
- Today Hub row date stem format, currently `YYYY-MM-DD`
- Markdown/wiki-link syntax

Clients may add support for new configurable fields in the future, but they should not infer
configurability for the fixed contract above.

## R2 Connection Settings

R2 is split into non-secret shared connection metadata and per-device secrets.

Current v1 shared metadata is stored in the top-level `r2` block in `settings-shared.json`:

```json
{
  "r2": {
    "endpoint": "https://<account>.r2.cloudflarestorage.com",
    "bucket": "bucket-name",
    "jurisdiction": "eu"
  }
}
```

Fields:

- `endpoint`: required when R2 is configured.
- `bucket`: required when R2 is configured.
- `jurisdiction`: optional; allowed values are `default`, `eu`, and `fedramp`. Omit when default.

The `r2` block identifies which bucket to use. It is not sufficient to perform signed R2 requests
without device-local keys.

Important implementation note: the desktop planning document discusses moving non-secret R2 fields
into `appSettings`. The current shared-vault compatibility shape remains the top-level `r2` block.
Mobile should read and preserve this block unless/until a later migration is explicitly specified.

## R2 Secrets

R2 `accessKeyId` and `secretAccessKey` are secrets.

Hard rules:

- Never write R2 secrets to `settings-shared.json`.
- Never write R2 secrets to `settings-local.json`.
- Never write R2 secrets to app-local caches, preferences, stores, fixtures, screenshots, logs,
  telemetry, crash reports, or test snapshots.
- The renderer/UI may set or replace secrets but must not read stored secrets back.
- UI may only know status: keys set, keys not set, or keychain unavailable.
- Replacing keys overwrites the prior per-device secret entry.
- If secure storage is unavailable, the client may use memory-only session storage, but must not
  write a plaintext fallback file.

Desktop stores secrets in the OS keychain / Secret Service. Android should use Android Keystore or
an equivalent encrypted credential store. The exact secure-storage implementation is platform
specific, but the no-plaintext and no-readback contract is shared.

Legacy desktop vaults may contain plaintext `r2.accessKeyId` and `r2.secretAccessKey` in
`settings-shared.json`. Clients that encounter these fields must treat them as a migration/security
case, not as the normal read path:

1. Move the keys into the platform secure store when possible.
2. Remove the plaintext fields from `settings-shared.json`.
3. Preserve `r2.endpoint`, `r2.bucket`, and `r2.jurisdiction`.
4. Never log or display the plaintext values during migration.

If a client cannot safely complete the migration, it should fail closed: do not use plaintext keys
as normal runtime state and do not create another plaintext copy.

## Device-Local Settings

The local settings file is:

```text
.eskerra/settings-local.json
```

This file is not intended to sync between devices. Current desktop fields include:

- `deviceName`
- `displayName`
- `deviceInstanceId`
- playlist known remote metadata / watermarks

Mobile may have its own local fields, but must preserve unknown keys if it writes this file.

Do not put shared vault layout settings or R2 secrets in `settings-local.json`.

## Theme and Other Legacy Shared Fields

Current desktop v1 keeps theme on its existing path:

- R2 object: `theme-preference.json` when R2 theme sync is active.
- Shared settings mirror: top-level `themePreference` in `settings-shared.json` for local fallback.

Do not assume theme is part of `appSettings` in v1.

Other top-level shared fields that may exist:

- `frontmatterProperties`
- `linkSnippetBlockedDomains`

Desktop currently keeps these readable for compatibility. Mobile should preserve them when writing
the shared settings file, even if it does not consume them.

## Read Flow

Recommended shared read behavior:

1. Read `.eskerra/settings-shared.json`.
2. Parse the top-level object. If the file is malformed, surface an appropriate settings-read
   problem and use defaults only where the app can safely continue.
3. Parse `appSettings` leniently:
   - missing or malformed `appSettings` resolves to `{}`;
   - missing/invalid per-key values resolve to defaults;
   - unknown keys remain in memory for future writes.
4. Resolve `vaultLayout` from `appSettings.vaultLayout`, falling back per field.
5. Resolve R2 shared connection metadata from the top-level `r2` block.
6. Resolve R2 secret status lazily from secure storage only when R2 functionality or the Cloud
   connection UI needs it.

Startup performance invariant:

Do not perform expensive vault scans, network calls, feed sync, keychain reads, or broad indexing on
the first-render path. A client may use a disposable local cache for first paint, then refresh from
the vault in the background. That cache is not authoritative.

## Write Flow

Recommended write behavior:

1. Start from the current on-disk `settings-shared.json`.
2. Apply only the setting changes the user made.
3. Preserve unknown top-level keys and unknown nested keys.
4. Omit values equal to defaults in `appSettings`.
5. Write the full JSON file atomically where the platform allows.
6. Update any local first-paint cache only after, or as part of, accepted runtime intent.

If a write fails after the user has changed a valid setting, the runtime should not silently revert
to the old behavior. Keep the accepted local value active if possible, store retry state only in
transient app-local storage, and retry when the vault becomes writable or settings are refreshed.

Concurrent-device writes are expected to be last-write-wins at the file sync layer. Syncthing-style
hard collisions may produce files containing `sync-conflict`; clients should not make that marker
configurable.

## Platform Notes for Android/Kotlin

For Kotlin, model the shared document with a permissive JSON representation:

- Decode known fields into typed data classes.
- Retain the original JSON object or unknown-field maps for write-back.
- Apply per-field validators for consumed settings.
- Do not use strict decoding that fails the whole `appSettings` document because one known field is
  invalid.

Recommended Kotlin defaults for `VaultLayoutConfig`:

```kotlin
data class VaultLayoutConfig(
    val inboxFolder: String = "Inbox",
    val generalFolder: String = "General",
    val assetsFolder: String = "Assets",
    val attachmentsSubfolder: String = "Attachments",
    val hubNoteFileName: String = "Today.md",
    val podcastFeedFileSuffix: String = "podcasts.md",
    val podcastEpisodeFilePrefix: String = "📻 ",
)
```

Keep validation behavior aligned with desktop. If Android ignores a setting for now, it should
still preserve it when writing `settings-shared.json`.

## Security Checklist

Before shipping any settings/R2 change in either client, verify:

- R2 secrets are absent from `settings-shared.json`.
- R2 secrets are absent from `settings-local.json`.
- R2 secrets are absent from app-local cache/preference/store files.
- No IPC/API/UI path returns raw stored secrets to a renderer/view layer.
- Logs/errors/telemetry contain only redacted secret status, never key material.
- Keychain/Keystore unavailable does not crash startup.
- Keychain/Keystore access is lazy, not part of first render.
- Valid custom vault layout changes only lookup/create targets; it does not migrate files.
- Unknown JSON keys survive read-modify-write.
- Invalid appSettings values fall back per key.

## Compatibility Summary

Desktop and Android can safely interoperate if they agree on these rules:

- Shared, synced non-secret settings live in `.eskerra/settings-shared.json`.
- Vault layout settings live in the sparse `appSettings.vaultLayout` object.
- Current v1 R2 connection metadata lives in the top-level `r2` block.
- R2 secrets live only in platform secure storage per device.
- Device-local identity and watermarks live in `.eskerra/settings-local.json`.
- Caches are disposable and non-authoritative.
- Layout changes never move or delete user files.
- Unknown fields are preserved.
