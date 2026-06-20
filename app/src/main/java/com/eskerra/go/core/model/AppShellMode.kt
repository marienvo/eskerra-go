package com.eskerra.go.core.model

/** Last selected top-level shell tab. Per-device only; never synced via git. */
enum class AppShellMode {
    HOME,
    PODCASTS;

    companion object {
        fun fromStored(value: String?): AppShellMode =
            entries.firstOrNull { it.name == value } ?: HOME
    }
}
