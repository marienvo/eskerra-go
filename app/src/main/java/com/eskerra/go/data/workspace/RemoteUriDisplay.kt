package com.eskerra.go.data.workspace

import java.net.URI

/** Sanitizes remote URLs for safe UI diagnostics (host and path only). */
object RemoteUriDisplay {

    fun sanitize(remoteUri: String?): String? {
        val trimmed = remoteUri?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        if (RemoteUriSecurity.containsEmbeddedCredentials(trimmed)) return null

        return try {
            val parsed = URI(trimmed)
            val host = parsed.host
                ?: parsed.schemeSpecificPart.removePrefix("//").substringBefore('/')
            if (host.isBlank()) return null
            val path = parsed.path?.trimEnd('/')?.removePrefix("/").orEmpty()
            if (path.isBlank()) host else "$host/$path"
        } catch (_: Exception) {
            trimmed
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("file://")
                .substringBefore('@')
                .trimEnd('/')
                .ifBlank { null }
        }
    }
}
