package com.eskerra.go.core.model

/** Per-device local settings — never synced via git. */
data class EskerraLocalSettings(
    val displayName: String = "",
    val deviceName: String = "",
    val deviceInstanceId: String = "",
    val playlistKnownUpdatedAtMs: Long? = null,
    val playlistKnownControlRevision: Long? = null
)
