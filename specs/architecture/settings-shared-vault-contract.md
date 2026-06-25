# Shared settings and vault contract (pointer)

**Canonical document:** notebox [`specs/architecture/settings-shared-vault-contract.md`](../../../notebox/specs/architecture/settings-shared-vault-contract.md) (sibling repo).

Edit the spec in the **notebox** repository, not here. This file exists so Eskerra Go agents and contributors find the contract without duplicating it.

## Eskerra Go interim gaps (vs canonical contract)

See the **Compatibility matrix (current)** section in the canonical doc. Summary:

| Area | Status |
|---|---|
| R2 secrets in Android Keystore (not shared JSON) | Not implemented |
| `hasR2Connection` + per-device credentials split | Not implemented |
| `appSettings.vaultLayout` consumed in podcast/inbox paths | Preserve-only stub |
| `.eskerra/settings-local.json` file I/O | DataStore substitute |
| `cloud.r2SyncEnabled` disable toggle | Planned in canonical spec; not in code |

Implementation work should close these gaps without changing the on-disk contract unless the canonical spec is updated first in notebox.
