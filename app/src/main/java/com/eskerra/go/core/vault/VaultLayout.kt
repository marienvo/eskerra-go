package com.eskerra.go.core.vault

/** Path constants mirroring `packages/eskerra-core/src/vaultLayout.ts`. */
object VaultLayout {
    const val ASSETS_DIRECTORY_NAME = "Assets"
    const val ATTACHMENTS_DIRECTORY_NAME = "Attachments"

    const val ESKERRA_DIR = ".eskerra"
    const val NOTEBOX_DIR = ".notebox"
    const val SHARED_SETTINGS_FILE = "settings-shared.json"
    const val LOCAL_SETTINGS_FILE = "settings-local.json"
    const val LEGACY_SETTINGS_FILE = "settings.json"
    const val PLAYLIST_FILE = "playlist.json"

    /** R2 object-key prefix whose root mirrors the vault root (device-only binaries). */
    const val BINARIES_PREFIX = "binaries/"

    const val SHARED_SETTINGS_PATH = "$ESKERRA_DIR/$SHARED_SETTINGS_FILE"
    const val LEGACY_SETTINGS_PATH = "$ESKERRA_DIR/$LEGACY_SETTINGS_FILE"
    const val PLAYLIST_PATH = "$ESKERRA_DIR/$PLAYLIST_FILE"
}
