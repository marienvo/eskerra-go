package com.eskerra.go.core.model

import kotlinx.serialization.json.JsonElement

/**
 * Shared vault settings stored in `.eskerra/settings-shared.json`.
 *
 * [extras] preserves desktop-only keys (themePreference, frontmatterProperties,
 * linkSnippetBlockedDomains) so they survive a mobile save without being stripped.
 */
data class EskerraSettings(
    val r2: R2Config? = null,
    val extras: Map<String, JsonElement> = emptyMap()
)
