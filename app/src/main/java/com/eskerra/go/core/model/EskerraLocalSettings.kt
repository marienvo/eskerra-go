package com.eskerra.go.core.model

/** Per-device local settings — never synced via git. */
data class EskerraLocalSettings(
    val displayName: String = "",
    val deviceName: String = "",
    val deviceInstanceId: String = "",
    val playlistKnownUpdatedAtMs: Long? = null,
    val playlistKnownControlRevision: Long? = null,
    val lastShellMode: AppShellMode = AppShellMode.HOME,
    val podcastEpisodeId: String? = null,
    val podcastMp3Url: String? = null,
    val podcastPositionMs: Long? = null,
    val podcastDurationMs: Long? = null,
    val podcastSnapshotUpdatedAtMs: Long? = null
)
