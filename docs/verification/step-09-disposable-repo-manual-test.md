# Step 9: Disposable private repo manual test

Use a **throwaway** private GitHub or GitLab repository. Do not use a valuable notes repo until this checklist passes.

Replace placeholders:

- `<owner>` — your GitHub/GitLab username or org
- `<repo>` — disposable test repository name
- `<token>` — short-lived personal access token with **minimal** repo read/write scope

Never paste a real token into issues, screenshots, or this document.

## Prerequisites

- [ ] Install a debug build on a device or emulator (`./scripts/install-debug.sh` or Android Studio).
- [ ] Confirm Java 17 unit tests and lint pass locally before manual testing.
- [ ] Create private repo `https://github.com/<owner>/<repo>.git` (or GitLab equivalent) with:
  - [ ] `Inbox/seed.md`
  - [ ] `Projects/read-only.md` (or similar non-`Inbox/` note)
- [ ] Create a fine-grained or classic token with **only** the permissions needed to read/write that repo.
- [ ] Revoke the token after testing if you will not reuse it.

## Setup and clone

- [ ] Open workspace setup (or sync settings on an existing workspace).
- [ ] Enter remote URL: `https://github.com/<owner>/<repo>.git` (no username/password in URL).
- [ ] Enter branch (for example `main`).
- [ ] Enter token in the masked password field.
- [ ] Complete clone/setup successfully.
- [ ] Open Inbox and confirm seeded note appears.

## Inbox edit and sync

- [ ] Edit an `Inbox/` note in the app.
- [ ] Tap **Sync now** once (do not double-tap rapidly on first pass).
- [ ] On the hosting site, open the latest commit from the app.
- [ ] Confirm the commit contains **only** `Inbox/` path changes (no accidental `Projects/` or other paths).

## Remote non-Inbox update

- [ ] On the hosting site, edit `Projects/read-only.md` (or another non-`Inbox/` file).
- [ ] In the app, tap **Sync now**.
- [ ] Open the updated non-`Inbox/` note in the reader.
- [ ] Confirm the note is **read-only** (no edit/save affordance for non-inbox notes).

## Auth failure (token revoked)

- [ ] Revoke or expire `<token>` on the hosting site.
- [ ] Tap **Sync now** in the app.
- [ ] Confirm the error message is safe (no token, no credential-bearing URL, no stack trace).
- [ ] Confirm local Inbox notes still open and editable offline.

## Offline behavior

- [ ] Disable network (airplane mode or emulator network off).
- [ ] Read an existing note, edit an `Inbox/` note, and save.
- [ ] Confirm local read/edit/save works without sync.
- [ ] Re-enable network when finished with offline checks.

## Security spot checks

- [ ] Inspect workspace `.git/config` on device (adb shell or exported workspace copy): `origin` URL must **not** contain `<token>` or userinfo.
- [ ] If practical, inspect app DataStore / workspace metadata (for example via backup-disabled device debugging): no `<token>` in preference keys or values listed in `DataStoreWorkspaceStore.NON_SECRET_PREFERENCE_KEY_NAMES`.
- [ ] Confirm token entry field remains masked during setup and sync settings.

## Keystore credential storage (device/emulator)

- [ ] After saving `<token>`, force-stop and relaunch the app.
- [ ] Tap **Sync now** or **Test connection** without re-entering the token.
- [ ] Confirm sync or connection succeeds (proves Keystore-backed read path on device).
- [ ] Optional: run `./gradlew :app:connectedDebugAndroidTest` with `EncryptedCredentialStoreAndroidTest` on an emulator.

## Sync hardening spot checks (optional)

- [ ] Double-tap **Sync now** quickly — only one sync should run.
- [ ] With local changes outside `Inbox/` (made via desktop Git), confirm sync is blocked with a clear message.
- [ ] After a successful sync, confirm Inbox list reflects remote/pull changes.

## Cleanup

- [ ] Revoke test token on the hosting site.
- [ ] Delete or archive the disposable repository when done.

## Deferred (not required for Step 9 sign-off)

- Real-device HTTPS quirks beyond this checklist
- SyncGitErrorMapper string-hardening
- Commit-before-fetch UX changes
- Background sync, SSH, conflict repair UI
